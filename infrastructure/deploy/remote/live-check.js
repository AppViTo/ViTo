// ¿Hay un evento en vivo? Consulta de solo lectura (feature 001, research R6). La corre
// live-check.sh con `mongosh --nodb` dentro del contenedor mongodb, como vito_deploy_ro.
// La contraseña llega por la variable de entorno, nunca en la línea de comandos.
//
// Las fechas son LocalDateTime sin zona (event-service): el margen de 1 h en cada punta
// las cubre. Con la hora de Colombia (UTC-5) el margen real es mayor y el chequeo bloquea
// de más, nunca de menos.
const password = encodeURIComponent(process.env.MONGO_DEPLOY_RO_PASSWORD || '');
const conn = new Mongo(`mongodb://vito_deploy_ro:${password}@localhost:27017/?authSource=admin`);
const now = Date.now();
const HOUR = 60 * 60 * 1000;

const events = conn.getDB('vito_events').getCollection('events')
  .find(
    {
      status: 'PUBLISHED',
      'details.startDate': { $lte: new Date(now + HOUR) },
      'details.endDate': { $gte: new Date(now - HOUR) },
    },
    { title: 1 },
  )
  .limit(20)
  .toArray()
  .map((e) => ({ id: String(e._id), title: e.title ?? null }));

const streams = conn.getDB('vito_streaming').getCollection('live_streams')
  .find({ status: { $in: ['LIVE', 'CONNECTING', 'BUFFERING'] } }, { eventId: 1 })
  .limit(20)
  .toArray()
  .map((s) => ({ id: String(s._id), eventId: s.eventId ?? null }));

print(JSON.stringify({ events, streams }));
