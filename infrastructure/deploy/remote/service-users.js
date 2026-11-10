// Usuarios de Mongo por servicio (T-I19, M12; Principio II): vito_<svc> con readWrite solo
// en la base del servicio. Idempotente: crea el usuario o le actualiza contraseña y rol.
//
// Se usa de dos formas, con la misma lista:
//   - ensure-service-users.sh, en cada despliegue o rollback (volumen ya inicializado);
//   - /docker-entrypoint-initdb.d de mongodb, en el primer arranque de un volumen vacío
//     (VPS nuevo o restauración en otra máquina), antes de que arranque ningún servicio.
//
// Credenciales por entorno, nunca en la línea de comandos: root en MONGO_ROOT_* (o en
// MONGO_INITDB_ROOT_* dentro de initdb) y una contraseña por servicio en MONGO_<SVC>_PASSWORD.
// Las contraseñas van sin codificar en MONGODB_URI (docker-compose.prod.yml): deben ser
// alfanuméricas (se generan con el openssl de docs/runbooks/aprovisionamiento.md).
//
// auth, chat y media comparten vito_db: deuda del Principio II registrada en T-I19.
const SERVICES = [
  { svc: 'auth', db: 'vito_db' },
  { svc: 'social', db: 'vito_social' },
  { svc: 'event', db: 'vito_events' },
  { svc: 'checkin', db: 'vito_checkin' },
  { svc: 'mosaic', db: 'vito_mosaic' },
  { svc: 'streaming', db: 'vito_streaming' },
  { svc: 'archive', db: 'vito_archive' },
  { svc: 'chat', db: 'vito_db' },
  { svc: 'media', db: 'vito_db' },
];

const env = process.env;
const passwordVar = (svc) => `MONGO_${svc.toUpperCase()}_PASSWORD`;

const missing = SERVICES.map((s) => passwordVar(s.svc)).filter((v) => !env[v]);
if (missing.length) {
  // Solo los nombres: nunca un valor
  throw new Error(`faltan contraseñas: ${missing.join(' ')}`);
}
const invalid = SERVICES.map((s) => passwordVar(s.svc)).filter((v) => !/^[A-Za-z0-9]+$/.test(env[v]));
if (invalid.length) {
  throw new Error(`contraseñas con caracteres no alfanuméricos (rompen MONGODB_URI): ${invalid.join(' ')}`);
}

const rootUser = env.MONGO_ROOT_USERNAME || env.MONGO_INITDB_ROOT_USERNAME;
const rootPassword = env.MONGO_ROOT_PASSWORD || env.MONGO_INITDB_ROOT_PASSWORD;
const e = encodeURIComponent;
// 127.0.0.1 y no localhost: en initdb mongod solo escucha en IPv4 y localhost puede resolver a ::1
const admin = new Mongo(`mongodb://${e(rootUser)}:${e(rootPassword)}@127.0.0.1:27017/?authSource=admin`).getDB('admin');

for (const { svc, db } of SERVICES) {
  const user = `vito_${svc}`;
  const pwd = env[passwordVar(svc)];
  const roles = [{ role: 'readWrite', db }];
  if (admin.getUser(user)) {
    admin.updateUser(user, { pwd, roles });
  } else {
    admin.createUser({ user, pwd, roles });
  }
}
print(`ok ${SERVICES.length}`);
