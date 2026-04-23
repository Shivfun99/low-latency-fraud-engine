import { useState, useEffect, useRef, useCallback } from 'react';
const WS_URL = `ws:
const RECONNECT_INTERVAL = 3000;
export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const [stats, setStats] = useState({
    totalAlerts: 0,
    totalDecisions: 0,
    blockedCount: 0,
    reviewCount: 0,
    allowedCount: 0,
    connectedClients: 0,
    uptimeSeconds: 0,
  });
  const [alerts, setAlerts] = useState([]);
  const [decisions, setDecisions] = useState([]);
  const wsRef = useRef(null);
  const reconnectRef = useRef(null);
  const connect = useCallback(() => {
    try {
      const ws = new WebSocket(WS_URL);
      wsRef.current = ws;
      ws.onopen = () => {
        console.log('[WS] Connected');
        setConnected(true);
        if (reconnectRef.current) {
          clearInterval(reconnectRef.current);
          reconnectRef.current = null;
        }
      };
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          switch (msg.type) {
            case 'CONNECTED':
              if (msg.stats) setStats(msg.stats);
              if (msg.recentAlerts) setAlerts(msg.recentAlerts);
              if (msg.recentDecisions) setDecisions(msg.recentDecisions);
              break;
            case 'FRAUD_ALERT':
              setAlerts((prev) => {
                const index = prev.findIndex(a => a.alert_id === msg.data.alert_id);
                if (index !== -1) {
                  const updated = [...prev];
                  updated[index] = msg.data;
                  return updated;
                }
                return [msg.data, ...prev].slice(0, 50);
              });
              break;
            case 'FRAUD_DECISION':
              setDecisions((prev) => [msg.data, ...prev].slice(0, 100));
              break;
            case 'STATS_UPDATE':
              if (msg.stats) setStats(msg.stats);
              break;
            case 'PONG':
              break;
            default:
              break;
          }
        } catch (e) {
          console.error('[WS] Parse error:', e);
        }
      };
      ws.onclose = () => {
        console.log('[WS] Disconnected');
        setConnected(false);
        wsRef.current = null;
        if (!reconnectRef.current) {
          reconnectRef.current = setInterval(() => {
            console.log('[WS] Reconnecting...');
            connect();
          }, RECONNECT_INTERVAL);
        }
      };
      ws.onerror = (err) => {
        console.error('[WS] Error:', err);
        ws.close();
      };
    } catch (e) {
      console.error('[WS] Connection failed:', e);
    }
  }, []);
  useEffect(() => {
    connect();
    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectRef.current) clearInterval(reconnectRef.current);
    };
  }, [connect]);
  const sendMessage = useCallback((msg) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(msg));
    }
  }, []);
  return { connected, stats, alerts, decisions, sendMessage };
}
