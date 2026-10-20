import { useState, useEffect, useRef } from 'react';

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const [stats, setStats] = useState({});
  const [alerts, setAlerts] = useState([]);
  const [decisions, setDecisions] = useState([]);
  const wsRef = useRef(null);

  useEffect(() => {
    let disposed = false;
    let retryTimer;
    function connect() {
      if (disposed) return;
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const ws = new WebSocket(
        import.meta.env.VITE_WS_URL || `${protocol}//${window.location.host}/ws`
      );
      wsRef.current = ws;
      ws.onopen = () => {
        if (!disposed) setConnected(true);
      };
      ws.onmessage = (event) => {
        if (disposed) return;
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'CONNECTED') {
            setStats(msg.stats || {});
            setAlerts(msg.recentAlerts || []);
            setDecisions(msg.recentDecisions || []);
          } else if (msg.type === 'FRAUD_ALERT') {
            setAlerts((previous) =>
              [msg.data, ...previous.filter((a) => a.alert_id !== msg.data.alert_id)].slice(0, 50)
            );
          } else if (msg.type === 'FRAUD_DECISION') {
            setDecisions((previous) =>
              [
                msg.data,
                ...previous.filter((d) => d.transaction_id !== msg.data.transaction_id),
              ].slice(0, 100)
            );
          } else if (msg.type === 'STATS_UPDATE' || msg.type === 'STATS') {
            setStats(msg.stats || {});
          }
        } catch (error) {
          console.error('[WS] Invalid server message:', error);
        }
      };
      ws.onclose = () => {
        if (disposed) return;
        setConnected(false);
        wsRef.current = null;
        retryTimer = setTimeout(connect, 3000);
      };
      ws.onerror = () => ws.close();
    }
    connect();
    return () => {
      disposed = true;
      clearTimeout(retryTimer);
      if (wsRef.current) wsRef.current.close();
    };
  }, []);
  return { connected, stats, alerts, decisions };
}
