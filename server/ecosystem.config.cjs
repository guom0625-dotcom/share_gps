module.exports = {
  apps: [
  {
    name: 'caddy',
    script: '/usr/local/bin/caddy',
    args: 'run --config /root/projects/share_gps/server/Caddyfile',
    cwd: '/root/projects/share_gps/server',
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
  }],
};
