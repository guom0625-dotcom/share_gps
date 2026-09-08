module.exports = {
  apps: [
  {
    name: 'caddy',
    script: '/home/guom0625/.local/bin/caddy',
    args: 'run --config /home/guom0625/workspace/share_gps/server/Caddyfile',
    cwd: '/home/guom0625/workspace/share_gps/server',
    interpreter: 'none',
    watch: false,
    restart_delay: 1000,
    max_restarts: 10,
    log_file: './logs/caddy.log',
    error_file: './logs/caddy-error.log',
    out_file: './logs/caddy-out.log',
  },
  {
    name: 'share-gps',
    script: 'src/index.ts',
    interpreter: 'node',
    interpreter_args: '--import tsx/esm',
    cwd: __dirname,
    watch: false,
    env: {
      NODE_ENV: 'production',
      PORT: '3000',
      // Caddy가 외부를 담당하므로 로컬호스트만 listen
      HOST: '127.0.0.1',
      DB_PATH: './data/share_gps.sqlite3',
      LOG_LEVEL: 'info',
    },
    restart_delay: 3000,
    max_restarts: 10,
    log_file: './logs/combined.log',
    error_file: './logs/error.log',
    out_file: './logs/out.log',
  },
  {
    // cron이 없는 환경이라 pm2의 cron_restart로 5분마다 1회 실행
    name: 'duckdns',
    script: '/home/guom0625/workspace/share_gps/scripts/duckdns-update.sh',
    interpreter: 'bash',
    cwd: '/home/guom0625/workspace/share_gps/scripts',
    autorestart: false,
    cron_restart: '*/5 * * * *',
    log_file: '/home/guom0625/workspace/share_gps/server/logs/duckdns.log',
  }],
};
