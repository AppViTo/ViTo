// Bases propias de chat-service y media-service (T-B40, deuda del Principio II de T-I19).
// Hasta T-B40 compartían vito_db con auth-service; desde T-B40 cada una tiene la suya.
//
// LAYOUT=split  mueve vito_db.<col> a vito_chat / vito_media (despliegue de T-B40 en adelante)
// LAYOUT=shared las devuelve a vito_db (rollback a un release anterior a T-B40)
// PLAN=1        solo cuenta lo que hay que mover y no toca nada
//
// Idempotente: si la colección ya está donde corresponde, no hace nada. Si está en los dos
// lados y el destino está vacío (p. ej. un índice creado al arrancar), borra el destino vacío
// y mueve. Si los dos lados tienen documentos, falla antes de mover nada: lo decide un lead.
//
// renameCollection entre bases copia los documentos y los índices y borra el origen; en el
// Mongo standalone de producción (colecciones pequeñas) tarda segundos. Lo invoca
// migrate-service-dbs.sh, que para chat y media antes de mover.
//
// La presencia de este archivo en un bundle es lo que deploy.sh usa para saber que ese
// release espera las bases propias: no renombrarlo sin actualizar deploy.sh.
const MOVES = [
  { shared: 'vito_db', split: 'vito_chat', cols: ['chat_messages', 'chat_rooms'] },
  { shared: 'vito_db', split: 'vito_media', cols: ['media'] },
];

const env = process.env;
const layout = env.LAYOUT;
if (layout !== 'split' && layout !== 'shared') {
  throw new Error('LAYOUT tiene que ser split o shared');
}
const planOnly = env.PLAN === '1';
const back = layout === 'shared';

const e = encodeURIComponent;
const conn = new Mongo(
  `mongodb://${e(env.MONGO_ROOT_USERNAME)}:${e(env.MONGO_ROOT_PASSWORD)}@127.0.0.1:27017/?authSource=admin`,
);
const admin = conn.getDB('admin');
const exists = (db, col) => conn.getDB(db).getCollectionNames().includes(col);
const count = (db, col) => conn.getDB(db).getCollection(col).estimatedDocumentCount();

// 1. Plan completo antes de mover nada
const pending = [];
const conflicts = [];
for (const m of MOVES) {
  const from = back ? m.split : m.shared;
  const to = back ? m.shared : m.split;
  for (const col of m.cols) {
    if (!exists(from, col)) continue;
    const dropTarget = exists(to, col);
    if (dropTarget && count(to, col) > 0 && count(from, col) > 0) {
      conflicts.push(`${from}.${col} y ${to}.${col}`);
      continue;
    }
    if (dropTarget && count(from, col) === 0) {
      // El origen está vacío y el destino ya tiene la colección: basta con quitar el origen
      pending.push({ from, to, col, action: 'drop-source' });
      continue;
    }
    pending.push({ from, to, col, action: dropTarget ? 'drop-target' : 'rename' });
  }
}
if (conflicts.length) {
  throw new Error(`conflicto, las dos bases tienen documentos: ${conflicts.join(', ')}`);
}
if (planOnly) {
  print(`pending ${pending.length}`);
  quit(0);
}

// 2. Mover
for (const p of pending) {
  if (p.action === 'drop-source') {
    conn.getDB(p.from).getCollection(p.col).drop();
    print(`borrada ${p.from}.${p.col} (vacía; ya existe ${p.to}.${p.col})`);
    continue;
  } else {
    const res = admin.runCommand({
      renameCollection: `${p.from}.${p.col}`,
      to: `${p.to}.${p.col}`,
      dropTarget: p.action === 'drop-target',
    });
    if (res.ok !== 1) throw new Error(`renameCollection ${p.from}.${p.col}: ${res.errmsg}`);
  }
  print(`movida ${p.from}.${p.col} -> ${p.to}.${p.col}`);
}
print(`ok ${pending.length}`);
