'use strict';

// 将 SCF Web 函数的 HTTP 请求适配为 index.js 的 handler event，避免两套业务逻辑。
const http = require('http');
const { main_handler } = require('./index');

const port = Number(process.env.PORT || process.env.SCF_RUNTIME_PORT || 9000);

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = '';
    request.setEncoding('utf8');
    request.on('data', (chunk) => {
      body += chunk;
      if (body.length > 1024 * 1024) {
        reject(new Error('请求体超过 1 MB'));
        request.destroy();
      }
    });
    request.on('end', () => resolve(body));
    request.on('error', reject);
  });
}

const server = http.createServer(async (request, reply) => {
  try {
    if (request.method === 'GET' && request.url === '/health') {
      reply.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      reply.end(JSON.stringify({ ok: true }));
      return;
    }
    const result = await main_handler({
      httpMethod: request.method,
      path: request.url,
      headers: request.headers,
      body: await readBody(request)
    });
    reply.writeHead(result.statusCode || 200, result.headers || {});
    reply.end(result.body || '');
  } catch (error) {
    console.error(error);
    reply.writeHead(500, { 'content-type': 'application/json; charset=utf-8' });
    reply.end(JSON.stringify({ error: '服务暂时不可用' }));
  }
});

server.listen(port, '0.0.0.0', () => console.log(`evaluate-reading listening on ${port}`));
