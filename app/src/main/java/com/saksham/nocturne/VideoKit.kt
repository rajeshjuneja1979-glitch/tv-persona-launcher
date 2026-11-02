package com.saksham.nocturne

/**
 * JavaScript "video agent" that runs in the page AND in every frame inside it (players almost always live in
 * an iframe from another site, which the page's own script can't reach).
 *
 *  - Every frame that has a <video> reports its state once a second. Frames pass the report up with
 *    postMessage until the top frame hands it to the app through NocturneBridge.report().
 *  - The app sends commands (play, pause, seek, speed, full, sub, audio) to the top frame, which applies them
 *    to its own video and passes them down into every iframe.
 *  - "Full screen" is done with CSS (the video and the iframes around it are stretched over the whole page),
 *    so it needs no user gesture and Back simply undoes it.
 *  - options() lists the page's own Sub / Dub / Server / language buttons so the app can show them in a list,
 *    and pick(i) presses one of them.
 *  - zoom() sets the page scale through the viewport meta tag.
 *
 * No dollar signs in the script: it lives in a Kotlin raw string.
 */
object VideoKit {
    val AGENT: String = """
(function () {
  if (window.__ncv) return;
  var isTop = (window.top === window);
  var fullOn = false;

  function bridge(s) {
    try { if (window.NocturneBridge) window.NocturneBridge.report(s); } catch (e) {}
  }
  function toParent(d) {
    try { window.parent.postMessage(d, '*'); } catch (e) {}
  }
  function down(d) {
    var fr = document.querySelectorAll('iframe');
    for (var i = 0; i < fr.length; i++) {
      try { fr[i].contentWindow.postMessage(d, '*'); } catch (e) {}
    }
  }
  function vids() { return [].slice.call(document.querySelectorAll('video')); }

  function best() {
    var list = vids(), b = null, bs = -1;
    for (var i = 0; i < list.length; i++) {
      var v = list[i], r = v.getBoundingClientRect();
      var playing = !v.paused && !v.ended && v.readyState > 1;
      var area = r.width * r.height;
      if (area < 1 && !playing) continue;
      var score = (playing ? 1e9 : 0) + area;
      if (score > bs) { bs = score; b = v; }
    }
    return b;
  }

  function state(v) {
    var subs = [], auds = [], i;
    try {
      var tt = v.textTracks;
      for (i = 0; tt && i < tt.length; i++) {
        if (tt[i].kind === 'subtitles' || tt[i].kind === 'captions') {
          subs.push({ i: i, l: tt[i].label || tt[i].language || ('Track ' + (i + 1)), on: tt[i].mode === 'showing' });
        }
      }
    } catch (e) {}
    try {
      var at = v.audioTracks;
      for (i = 0; at && i < at.length; i++) {
        auds.push({ i: i, l: at[i].label || at[i].language || ('Audio ' + (i + 1)), on: !!at[i].enabled });
      }
    } catch (e) {}
    return {
      p: !v.paused && !v.ended,
      t: v.currentTime || 0,
      d: isFinite(v.duration) ? v.duration : 0,
      r: v.playbackRate || 1,
      f: fullOn,
      s: subs,
      a: auds
    };
  }

  function tick() {
    var v = best();
    if (!v) return;
    var msg = { __nc: 'rep', s: state(v) };
    if (isTop) bridge(JSON.stringify(msg.s)); else toParent(msg);
  }

  function ensureCss() {
    if (document.getElementById('__nc_vcss')) return;
    var s = document.createElement('style');
    s.id = '__nc_vcss';
    s.textContent =
      'html.__nc_full,html.__nc_full body,html.__nc_anc,html.__nc_anc body{overflow:hidden!important;background:#000!important}' +
      'html.__nc_full video.__nc_main{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
      'max-width:none!important;max-height:none!important;margin:0!important;transform:none!important;z-index:2147483646!important;' +
      'background:#000!important;object-fit:contain!important}';
    (document.head || document.documentElement).appendChild(s);
  }

  function setFull(v, on) {
    fullOn = on;
    ensureCss();
    var de = document.documentElement, list = vids(), i;
    if (on) {
      de.classList.add('__nc_full');
      v.classList.add('__nc_main');
    } else {
      de.classList.remove('__nc_full');
      for (i = 0; i < list.length; i++) list[i].classList.remove('__nc_main');
    }
    if (!isTop) toParent({ __nc: 'frame', on: on });
  }

  // A child frame went full: stretch its <iframe> element over this page, then tell our own parent.
  function frameFull(src, on) {
    ensureCss();
    var fr = document.querySelectorAll('iframe');
    for (var i = 0; i < fr.length; i++) {
      var f = fr[i], match = false;
      try { match = (f.contentWindow === src); } catch (e) {}
      if (!match) continue;
      if (on) {
        if (!f.hasAttribute('data-nc-old')) f.setAttribute('data-nc-old', f.getAttribute('style') || '');
        f.setAttribute('style',
          'position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
          'max-width:none!important;max-height:none!important;border:0!important;margin:0!important;' +
          'z-index:2147483647!important;background:#000!important;transform:none!important');
        document.documentElement.classList.add('__nc_anc');
      } else {
        if (f.hasAttribute('data-nc-old')) {
          f.setAttribute('style', f.getAttribute('data-nc-old'));
          f.removeAttribute('data-nc-old');
        }
        document.documentElement.classList.remove('__nc_anc');
      }
    }
    if (!isTop) toParent({ __nc: 'frame', on: on });
  }

  function run(c, a) {
    var v = best();
    if (!v) return;
    try {
      if (c === 'play') {
        var p = v.play();
        if (p && p.catch) p.catch(function () {});
      } else if (c === 'pause') {
        v.pause();
      } else if (c === 'seek') {
        var t = v.currentTime + a;
        var dur = isFinite(v.duration) ? v.duration : t;
        v.currentTime = Math.max(0, Math.min(dur, t));
      } else if (c === 'speed') {
        v.playbackRate = a;
      } else if (c === 'sub') {
        var tt = v.textTracks;
        for (var i = 0; i < tt.length; i++) {
          if (tt[i].kind === 'subtitles' || tt[i].kind === 'captions') tt[i].mode = (i === a) ? 'showing' : 'disabled';
        }
      } else if (c === 'audio') {
        var at = v.audioTracks;
        if (at) for (var j = 0; j < at.length; j++) at[j].enabled = (j === a);
      } else if (c === 'full') {
        setFull(v, !!a);
      }
    } catch (e) {}
  }

  // ---- the page's own Sub / Dub / Server / language buttons (top frame only)
  var OPT = /(\bsub\b|\bdub\b|subbed|dubbed|hardsub|softsub|hard sub|soft sub|server|\braw\b|\bmulti\b|\bhd[- ]?[0-9]|english|hindi|japanese|tamil|telugu|spanish|french|portuguese|arabic|korean|chinese)/i;
  var opts = [];

  function isAct(el) {
    var n = el;
    for (var k = 0; k < 2 && n; k++) {
      var c = (typeof n.className === 'string') ? n.className : '';
      if (/\s(active|selected|current|checked|on|is-active)\s/i.test(' ' + c + ' ')) return true;
      if (n.getAttribute && (n.getAttribute('aria-selected') === 'true' ||
          n.getAttribute('aria-pressed') === 'true' || n.getAttribute('aria-current') === 'true')) return true;
      n = n.parentElement;
    }
    return false;
  }

  function scan() {
    var els = document.querySelectorAll('a,button,li,span,label,div,option');
    var c = [], i, j;
    for (i = 0; i < els.length && c.length < 80; i++) {
      var el = els[i];
      var raw = el.textContent || '';
      if (raw.length > 60) continue;
      var tx = raw.replace(/\s+/g, ' ').trim();
      if (tx.length < 2 || tx.length > 28 || !OPT.test(tx)) continue;
      var r = el.getBoundingClientRect();
      if (r.width < 6 || r.height < 6) continue;
      c.push({ el: el, t: tx });
    }
    var out = [];
    opts = [];
    for (i = 0; i < c.length; i++) {
      var inner = false;
      for (j = 0; j < c.length; j++) {
        if (i !== j && c[i].el !== c[j].el && c[i].el.contains(c[j].el)) { inner = true; break; }
      }
      if (inner) continue;
      opts.push(c[i].el);
      out.push({ t: c[i].t, a: isAct(c[i].el) });
    }
    return out;
  }

  function pick(i) {
    var el = opts[i];
    if (!el) return 0;
    try {
      if (el.scrollIntoView) el.scrollIntoView({ block: 'center' });
      var a = el.closest ? el.closest('a') : null;
      if (a) a.setAttribute('target', '_self');
      el.click();
    } catch (e) {}
    return 1;
  }

  // Page scale: width is chosen so the page reflows to fit the screen at the chosen zoom.
  function zoom(p, dpWidth) {
    if (!isTop) return 0;
    var s = p / 100;
    var w = Math.round((dpWidth || window.screen.width || 1280) / s);
    var m = document.querySelector('meta[name=viewport]');
    if (!m) {
      var h = document.head || document.documentElement;
      if (!h) return 0;
      m = document.createElement('meta');
      m.setAttribute('name', 'viewport');
      h.appendChild(m);
    }
    m.setAttribute('content', 'width=' + w + ',initial-scale=' + s + ',minimum-scale=' + s + ',maximum-scale=' + s + ',user-scalable=no');
    return 1;
  }

  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || typeof d !== 'object' || !d.__nc) return;
    if (d.__nc === 'rep') {
      if (isTop) bridge(JSON.stringify(d.s)); else toParent(d);
    } else if (d.__nc === 'cmd') {
      run(d.c, d.a);
      down(d);
    } else if (d.__nc === 'frame') {
      frameFull(e.source, d.on);
    }
  }, false);

  window.__ncv = {
    cmd: function (c, a) { run(c, a); down({ __nc: 'cmd', c: c, a: a }); return 1; },
    options: scan,
    pick: pick,
    zoom: zoom
  };

  setInterval(tick, 1000);
})();
"""
}
