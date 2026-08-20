/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // CloudFuze brand palette - blue, never violet.
        brand: {
          DEFAULT: '#0129AC',
          deep: '#0129AC',
          bright: '#0C18D4',
          light: '#809EFC',
          tint: '#E1ECFF',
          ink: '#01133F',
        },
        ink: {
          DEFAULT: '#000000',
          body: '#2e2e2e',
          muted: '#707070',
        },
        surface: {
          DEFAULT: '#ffffff',
          offwhite: '#F6F6F6',
          line: '#EBEBEB',
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
        DEFAULT: '4px',
        card: '10px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(1, 41, 172, 0.04), 0 8px 24px -12px rgba(1, 19, 63, 0.16)',
        pop: '0 24px 60px -24px rgba(1, 19, 63, 0.35)',
        rail: 'inset -1px 0 0 rgba(255,255,255,0.06)',
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
