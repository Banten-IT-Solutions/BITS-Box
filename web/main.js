// BITS Box landing
const $ = s => document.querySelector(s);
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const finePointer = matchMedia('(pointer: fine)').matches;

// smooth scroll (Lenis) + anchor offset untuk nav fixed
const lenis =
  !reduceMotion && typeof Lenis !== 'undefined'
    ? new Lenis({ lerp: 0.1, wheelMultiplier: 1.05 })
    : null;
if (lenis) {
  const raf = t => {
    lenis.raf(t);
    requestAnimationFrame(raf);
  };
  requestAnimationFrame(raf);
  document.querySelectorAll('a[href^="#"]').forEach(a =>
    a.addEventListener('click', e => {
      const target = document.querySelector(a.getAttribute('href'));
      if (target) {
        e.preventDefault();
        lenis.scrollTo(target, { offset: -76 });
      }
    })
  );
}

// velocity skew pada marquee (efek jelly saat scroll cepat)
if (lenis) {
  const marquee = $('.marquee');
  let skewReset;
  lenis.on('scroll', ({ velocity }) => {
    if (!marquee) return;
    const skew = Math.max(-8, Math.min(8, velocity * 0.9));
    marquee.style.transform = `skewX(${-skew}deg)`;
    clearTimeout(skewReset);
    skewReset = setTimeout(() => (marquee.style.transform = ''), 120);
  });
}

// reveal on scroll
const io = new IntersectionObserver(
  entries => {
    for (const e of entries) {
      if (e.isIntersecting) {
        e.target.classList.add('in');
        io.unobserve(e.target);
      }
    }
  },
  { threshold: 0.12 }
);
document.querySelectorAll('.reveal').forEach(el => io.observe(el));

// nav border + scroll progress
const nav = $('.nav');
const bar = document.createElement('div');
bar.className = 'progress';
document.body.prepend(bar);
addEventListener(
  'scroll',
  () => {
    nav.classList.toggle('scrolled', scrollY > 12);
    const max = document.documentElement.scrollHeight - innerHeight;
    bar.style.transform = `scaleX(${max > 0 ? scrollY / max : 0})`;
  },
  { passive: true }
);

// tilt on cards + flow phone (lerp ala lenis)
if (finePointer && !reduceMotion) {
  document.querySelectorAll('.card, .flow-phone').forEach(el => {
    let tx = 0,
      ty = 0,
      cx = 0,
      cy = 0,
      raf = null;
    const loop = () => {
      // kembali ke netral lebih cepat (0.22) daripada mengejar pointer (0.12)
      const f = tx === 0 && ty === 0 ? 0.22 : 0.12;
      cx += (tx - cx) * f;
      cy += (ty - cy) * f;
      el.style.transform = `perspective(700px) rotateX(${cy.toFixed(3)}deg) rotateY(${cx.toFixed(3)}deg) translate(-2px, -2px)`;
      if (Math.abs(tx - cx) > 0.02 || Math.abs(ty - cy) > 0.02) {
        raf = requestAnimationFrame(loop);
      } else {
        raf = null;
        if (tx === 0 && ty === 0) el.style.transform = '';
      }
    };
    const kick = () => {
      if (!raf) raf = requestAnimationFrame(loop);
    };
    el.addEventListener('pointermove', e => {
      const r = el.getBoundingClientRect();
      tx = ((e.clientX - r.left) / r.width - 0.5) * 6;
      ty = -((e.clientY - r.top) / r.height - 0.5) * 6;
      kick();
    });
    el.addEventListener('pointerleave', () => {
      if (raf) cancelAnimationFrame(raf);
      raf = null;
      cx = cy = tx = ty = 0;
      el.style.transform = ''; // reset instan, tanpa ekor lerp
    });
  });
}

// accordion FAQ: buka & tutup smooth (class .open + double rAF untuk first-open)
document.querySelectorAll('.faq details').forEach(d => {
  d.querySelector('summary').addEventListener('click', e => {
    e.preventDefault();
    if (d.open) {
      d.classList.remove('open');
      d.classList.add('closing');
      setTimeout(() => {
        d.open = false;
        d.classList.remove('closing');
      }, 340);
    } else {
      d.open = true; // render konten dulu (masih 0fr)
      requestAnimationFrame(() => requestAnimationFrame(() => d.classList.add('open')));
    }
  });
});

// latest release version badge
fetch('https://api.github.com/repos/Banten-IT-Solutions/BITS-Box/releases/latest')
  .then(r => (r.ok ? r.json() : Promise.reject()))
  .then(d => {
    const el = $('#cta-version');
    if (el) el.textContent = d.tag_name || '';
  })
  .catch(() => {});
