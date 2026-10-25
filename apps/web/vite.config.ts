import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers';

const platformTarget = process.env.VITE_PLATFORM_TARGET ?? 'http://127.0.0.1:8084';
const enterpriseTarget = process.env.VITE_ENTERPRISE_TARGET ?? 'http://127.0.0.1:8081';

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: 'src/components.d.ts',
      resolvers: [AntDesignVueResolver({ importStyle: false })]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: platformTarget,
        changeOrigin: false
      },
      '/ent': {
        target: enterpriseTarget,
        changeOrigin: false,
        rewrite: (path) => path.replace(/^\/ent/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.ts']
  }
});
