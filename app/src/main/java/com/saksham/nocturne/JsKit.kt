package com.saksham.nocturne

/**
 * JavaScript that runs inside the page so the cursor can work on the DOM directly.
 *
 * click():  looks at every element stacked under the cursor, skips anything that looks like an ad or an invisible
 *           click-hijack layer, finds the real link or button underneath, forces links to open in the same tab,
 *           and fires the pointer and mouse events on it. Fields that need typing ask for the keyboard.
 *           Frames, canvases, videos and dropdowns can't be reached this way, so it answers "native"
 *           and the app sends a real touch tap instead.
 * move():   fires hover events and reports whether the thing under the cursor is clickable.
 * scroll(): scrolls the scrollable box under the cursor, or the page.
 * hideAds(): injects CSS that hides common ad containers.
 *
 * Note: events made by script are "untrusted". A few sites ignore them (some fullscreen or play buttons).
 * For those, switch the click method to TAP in the site menu.
 */
object JsKit {
    private val selectors = listOf(
        "ins.adsbygoogle", ".adsbygoogle", "[id^=\"google_ads\"]", "[id^=\"div-gpt-ad\"]",
        "[id*=\"ad-slot\"]", "[class*=\"ad-slot\"]", "[data-ad-slot]", "[data-ad-client]",
        "iframe[src*=\"doubleclick.net\"]", "iframe[src*=\"googlesyndication.com\"]",
        "iframe[src*=\"googleadservices.com\"]", "iframe[src*=\"adnxs.com\"]",
        "iframe[src*=\"taboola\"]", "iframe[src*=\"outbrain\"]",
        "[id*=\"taboola\"]", "[class*=\"taboola\"]", "[id*=\"outbrain\"]", "[class*=\"outbrain\"]",
        "[aria-label=\"Advertisement\"]", "[aria-label=\"advertisement\"]",
        ".ad-banner", ".ad-container", ".ad-wrapper", ".advertisement", ".sponsored-content",
    ).joinToString(",")

    private const val INSTALL_TEMPLATE = """
window.__nc = (function () {
  var CLICKABLE = 'a[href],button,input,select,textarea,summary,label,video,[role=button],[role=link],[role=menuitem],[role=tab],[role=checkbox],[role=switch],[onclick]';
  var AD_TOKEN = /(^|[\s_-])(ad|ads|adv|advert|advertisement|adsbygoogle|sponsor|sponsored)(?![A-Za-z0-9])/i;
  var AD_SRC = /doubleclick|googlesyndication|googleadservices|adnxs|taboola|outbrain|popads|propeller|exoclick|adsterra|clickadu|hilltopads/i;
  var CSS = '__SELECTORS__{display:none!important}';

  function pt(x, y, vw, vh) { return [x * window.innerWidth / vw, y * window.innerHeight / vh]; }

  function adish(el) {
    var cls = (typeof el.className === 'string') ? el.className : '';
    if (AD_TOKEN.test((el.id || '') + ' ' + cls)) return true;
    var src = el.src || el.href || '';
    return typeof src === 'string' && AD_SRC.test(src);
  }

  // A big see-through fixed layer with no text and nothing clickable inside is a click-hijack trap.
  function trap(el) {
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
    var r = el.getBoundingClientRect();
    if (r.width < window.innerWidth * 0.5 || r.height < window.innerHeight * 0.5) return false;
    if ((el.innerText || '').trim().length > 0) return false;
    if (el.querySelector('a[href],button,input,select,textarea,video')) return false;
    var bg = cs.backgroundColor;
    return bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent' || parseFloat(cs.opacity) < 0.05;
  }

  var skipped = false;

  // Returns the first real element under the point. Ads and click-hijack layers are skipped and switched to
  // pointer-events:none, so even a later real touch tap passes straight through them.
  function pick(x, y) {
    skipped = false;
    var stack = document.elementsFromPoint ? document.elementsFromPoint(x, y) : [document.elementFromPoint(x, y)];
    for (var i = 0; i < stack.length; i++) {
      var el = stack[i];
      if (!el || el === document.documentElement || el === document.body) continue;
      if (adish(el) || trap(el)) {
        skipped = true;
        el.style.setProperty('pointer-events', 'none', 'important');
        continue;
      }
      return el;
    }
    return null;
  }

  function holder(el) { return el.closest ? el.closest(CLICKABLE) : null; }

  function needsNative(el) {
    var n = el.tagName;
    return n === 'IFRAME' || n === 'FRAME' || n === 'CANVAS' || n === 'OBJECT' || n === 'EMBED' || n === 'VIDEO' || n === 'SELECT';
  }

  function fire(el, type, x, y) {
    var init = { bubbles: true, cancelable: true, view: window, clientX: x, clientY: y, button: 0 };
    var ev;
    if (type.indexOf('pointer') === 0 && window.PointerEvent) {
      init.pointerType = 'mouse';
      init.isPrimary = true;
      ev = new PointerEvent(type, init);
    } else {
      ev = new MouseEvent(type, init);
    }
    el.dispatchEvent(ev);
  }

  function hideAds() {
    if (document.getElementById('__nc_css')) return;
    var s = document.createElement('style');
    s.id = '__nc_css';
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  return {
    hideAds: hideAds,

    move: function (px, py, vw, vh) {
      var p = pt(px, py, vw, vh), el = pick(p[0], p[1]);
      if (!el) return 0;
      if (el !== window.__ncHover) {
        if (window.__ncHover) fire(window.__ncHover, 'mouseout', p[0], p[1]);
        fire(el, 'mouseover', p[0], p[1]);
        window.__ncHover = el;
      }
      fire(el, 'mousemove', p[0], p[1]);
      return (holder(el) || needsNative(el)) ? 1 : 0;
    },

    click: function (px, py, vw, vh) {
      var p = pt(px, py, vw, vh), x = p[0], y = p[1], el = pick(x, y);
      if (!el) return skipped ? 'blocked' : 'native';
      if (needsNative(el)) return 'native';
      var t = holder(el) || el;
      var tag = t.tagName;
      if (tag === 'INPUT') {
        var ty = (t.type || 'text').toLowerCase();
        if (['text', 'search', 'url', 'email', 'password', 'tel', 'number'].indexOf(ty) >= 0) { t.focus(); return 'text'; }
      }
      if (tag === 'TEXTAREA' || t.isContentEditable) { t.focus(); return 'text'; }
      if (tag === 'A') {
        if (adish(t)) return 'blocked';
        t.setAttribute('target', '_self');
        t.setAttribute('rel', 'noopener noreferrer');
      }
      fire(t, 'pointerdown', x, y);
      fire(t, 'mousedown', x, y);
      fire(t, 'pointerup', x, y);
      fire(t, 'mouseup', x, y);
      fire(t, 'click', x, y);
      return 'dom';
    },

    scroll: function (px, py, vw, vh, dy) {
      var p = pt(px, py, vw, vh);
      var d = dy * window.innerHeight / vh;
      var el = document.elementFromPoint(p[0], p[1]);
      while (el && el !== document.body && el !== document.documentElement) {
        var cs = getComputedStyle(el);
        if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 2) {
          var before = el.scrollTop;
          el.scrollTop += d;
          if (el.scrollTop !== before) return 1;
        }
        el = el.parentElement;
      }
      window.scrollBy(0, d);
      return 1;
    }
  };
})();
"""

    private val install: String = INSTALL_TEMPLATE.replace("__SELECTORS__", selectors)

    private fun call(expr: String): String =
        "(function(){if(!window.__nc){" + install + "}return " + expr + ";})()"

    fun hideAds(): String = call("__nc.hideAds()")

    fun move(x: Int, y: Int, vw: Int, vh: Int): String = call("__nc.move($x,$y,$vw,$vh)")

    fun click(x: Int, y: Int, vw: Int, vh: Int): String = call("__nc.click($x,$y,$vw,$vh)")

    fun scroll(x: Int, y: Int, vw: Int, vh: Int, dy: Int): String = call("__nc.scroll($x,$y,$vw,$vh,$dy)")
}
