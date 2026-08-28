/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // Neutara Technologies palette. The primary and the three lightest
        // steps are sampled straight from the logo artwork, so the interface
        // and the mark are the same blue rather than two that nearly match.
        brand: {
          DEFAULT: '#174F96',
          deep: '#0D2F5C',
          // The workhorse blue for interface chrome: the same hue as the brand
          // but light enough to sit on white without shouting.
          mid: '#3F7FC9',
          bright: '#1D63B8',
          light: '#8BA7CA',
          tint: '#E2E9F2',
          wash: '#F1F4F8',
          ink: '#0D2F5C',
        },
        // A real ink ramp. Pure #000 on white is harsh and reads cheap; a deep
        // blue-black is softer and pairs with the brand hue.
        ink: {
          DEFAULT: '#0F172A',
          body: '#3A4459',
          muted: '#6B7688',
          faint: '#98A1B2',
        },
        surface: {
          DEFAULT: '#ffffff',
          canvas: '#F6F7FA',
          raised: '#FBFCFE',
          offwhite: '#F6F6F6',
          line: '#E7EAF1',
          hair: '#F0F2F7',
        },
        // Passive states - waiting on someone else, nothing for HR to do.
        slate: {
          DEFAULT: '#8595BC',
          tint: '#F1F3F9',
          line: '#DDE2EE',
          ink: '#586780',
        },
        amber: {
          DEFAULT: '#E0A63C',
          tint: '#FFF8EC',
          ink: '#8A5A08',
        },
        // Softened companions to the accent set. The neon originals are right
        // for a 3px bar or a small icon but too loud across a panel, so large
        // surfaces and big numerals use these instead. Brand blue still leads.
        soft: {
          blue: '#A8BEEA',
          teal: '#6FD3CB',
          green: '#7FD8A6',
          sand: '#E8C79C',
          rose: '#EFA0A0',
        },
        accent: {
          teal: '#14CFC3',
          blue: '#0065FF',
          cyan: '#3FD6F1',
          green: '#20CC83',
          red: '#FF1F1F',
          orange: '#FE5833',
        },
      },
      fontFamily: {
        sans: ['Poppins', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
      },
      fontWeight: {
        // 600 is the heaviest approved CloudFuze weight.
        bold: '600',
        extrabold: '600',
        black: '600',
      },
      borderRadius: {
        // Softer than the old 4px everywhere. A people-facing tool should not
        // look like a terminal; 8-12px reads friendly without going cartoonish.
        sm: '6px',
        DEFAULT: '8px',
        card: '12px',
      },
      boxShadow: {
        // Real elevation, gently. Flat-everything made every surface compete.
        xs: '0 1px 2px rgba(15, 23, 42, 0.04)',
        card: '0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.03)',
        raised: '0 4px 14px -4px rgba(15, 23, 42, 0.10), 0 1px 2px rgba(15, 23, 42, 0.04)',
        pop: '0 24px 60px -24px rgba(1, 19, 63, 0.35)',
        rail: 'inset -1px 0 0 rgba(15, 23, 42, 0.06)',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(12px) scale(0.99)' },
          '100%': { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 180ms ease-out',
        'slide-up': 'slide-up 220ms cubic-bezier(0.16, 1, 0.3, 1)',
      },
    },
  },
  plugins: [],
}
