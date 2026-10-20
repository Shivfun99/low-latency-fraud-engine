import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const wsGateway = process.env.WS_GATEWAY_URL || 'http://localhost:3001';
const proxy = {
  '/ws': { target: wsGateway, ws: true },
  '/api/v1/cases': { target: process.env.ORCHESTRATOR_URL || 'http://localhost:8081' },
  '/api/v1/notifications': {
    target: process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:8083',
  },
  '/api/v1': { target: process.env.TRANSACTION_GATEWAY_URL || 'http://localhost:8080' },
  '/api': { target: wsGateway },
};
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, host: '0.0.0.0', proxy },
  preview: { host: '0.0.0.0', proxy },
});
