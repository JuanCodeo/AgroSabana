// Siembra los datos de demostracion de AgroSabana en Firebase (Authentication + Firestore).
//
// Uso (Node 18+):  node firebase/seed/seed.mjs app/google-services.json
//
// Requiere que Firestore tenga reglas temporales que permitan escribir a usuarios autenticados;
// despues de sembrar se publican las reglas definitivas de firebase/firestore.rules.
// Es idempotente: si una cuenta ya existe, inicia sesion con ella y sobrescribe sus documentos.

import { readFileSync } from "node:fs";

const config = JSON.parse(readFileSync(process.argv[2] ?? "app/google-services.json", "utf8"));
const PROJECT = config.project_info.project_id;
const API_KEY = config.client[0].api_key[0].current_key;
const PASSWORD = "Agro2026";
const FIRESTORE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;

// ---------- Utilidades REST ----------

async function post(url, body, token) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  if (!res.ok) throw Object.assign(new Error(JSON.stringify(json.error ?? json)), { json });
  return json;
}

/** Crea la cuenta en Authentication o inicia sesion si ya existe. Devuelve { uid, token }. */
async function account(email) {
  const auth = "https://identitytoolkit.googleapis.com/v1/accounts";
  try {
    const r = await post(`${auth}:signUp?key=${API_KEY}`, { email, password: PASSWORD, returnSecureToken: true });
    return { uid: r.localId, token: r.idToken };
  } catch (e) {
    if (!String(e.message).includes("EMAIL_EXISTS")) throw e;
    const r = await post(`${auth}:signInWithPassword?key=${API_KEY}`, { email, password: PASSWORD, returnSecureToken: true });
    return { uid: r.localId, token: r.idToken };
  }
}

/** Convierte un valor de JavaScript al formato tipado de la API REST de Firestore. */
function value(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, value(x)])) } };
}

/** Escribe (crea o reemplaza) el documento collection/id. */
async function setDoc(path, data, token) {
  const res = await fetch(`${FIRESTORE}/${path}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ fields: value(data).mapValue.fields }),
  });
  if (!res.ok) throw new Error(`${path}: ${await res.text()}`);
}

// ---------- Datos demo ----------

const geo = (lat, lng) => ({ lat, lng });
const PESOS = 100;

const people = {
  admin: { email: "admin@agrosabana.demo", name: "Administrador AgroSabana", phone: "302 000 0000", role: "ADMIN", status: "ACTIVO", municipality: "Mosquera", address: "Cra 3 # 5-20, Mosquera", location: geo(4.7059, -74.2302) },
  buyer: { email: "comprador@agrosabana.demo", name: "Juan Pérez", phone: "300 000 0000", role: "BUYER", status: "ACTIVO", municipality: "Funza", address: "Cra 15 # 8-21, Funza", location: geo(4.7166, -74.2116) },
  maria: { email: "maria@agrosabana.demo", name: "María Gómez", phone: "305 000 0000", role: "BUYER", status: "ACTIVO", municipality: "Bogotá", address: "Calle 26 # 68-10, Bogotá", location: geo(4.6560, -74.1000) },
  rosal: { email: "vendedor@agrosabana.demo", name: "Finca El Rosal", phone: "301 000 0000", role: "SELLER", status: "ACTIVO", municipality: "Funza", address: "Vereda El Rosal, Funza", location: geo(4.7351, -74.1932) },
  pradera: { email: "lacteos@agrosabana.demo", name: "Lácteos La Pradera", phone: "303 000 0000", role: "SELLER", status: "ACTIVO", municipality: "Madrid", address: "Vereda Puente de Piedra, Madrid", location: geo(4.7322, -74.2640) },
  apiario: { email: "apiario@agrosabana.demo", name: "Apiario Sabana", phone: "304 000 0000", role: "SELLER", status: "PENDIENTE", municipality: "Facatativá", address: "Vereda La Tribuna, Facatativá", location: geo(4.8136, -74.3545) },
};

console.log(`Proyecto ${PROJECT}: creando cuentas...`);
const ids = {};
for (const [key, p] of Object.entries(people)) {
  ids[key] = await account(p.email);
  console.log(`  ${p.email} -> ${ids[key].uid}`);
}
const token = ids.admin.token;

for (const [key, p] of Object.entries(people)) {
  await setDoc(`users/${ids[key].uid}`, { ...p, deliveryRadiusKm: 15, photoUrl: null }, token);
}

const product = (id, name, description, pesos, unit, stock, category, seller, status, sold = 0) => ({
  id,
  data: {
    name, description, priceCents: pesos * PESOS, unit, stock, category,
    sellerId: ids[seller].uid, sellerName: people[seller].name, municipality: people[seller].municipality,
    origin: people[seller].location, status, sold, photoUrl: null,
  },
});

const products = [
  product("fresa-500g", "Fresa x 500 g", "Fresa fresca recogida el mismo día, sin intermediarios.", 8500, "500 g", 24, "FRUTAS", "rosal", "APROBADO", 86),
  product("queso-campesino", "Queso campesino", "Queso fresco de leche entera, elaborado artesanalmente.", 14000, "unidad", 18, "LACTEOS", "pradera", "APROBADO", 54),
  product("huevos-aa-30", "Huevos AA x 30", "Huevos de gallina campesina, cubeta de 30 unidades.", 19900, "cubeta", 40, "HUEVOS", "pradera", "APROBADO", 41),
  product("lechuga-crespa", "Lechuga crespa", "Lechuga cosechada en la mañana, lista para consumir.", 2500, "unidad", 60, "HORTALIZAS", "rosal", "APROBADO", 73),
  product("mora-500g", "Mora x 500 g", "Mora de Castilla seleccionada para jugos y postres.", 7000, "500 g", 3, "FRUTAS", "rosal", "APROBADO", 38),
  product("uchuva-250g", "Uchuva x 250 g", "Uchuva dulce, empacada en canastilla.", 4500, "250 g", 0, "FRUTAS", "rosal", "APROBADO", 19),
  product("astromelias", "Ramo de astromelias", "Ramo de flor fresca cortada el mismo día.", 12000, "ramo", 15, "FLORES", "pradera", "APROBADO", 12),
  product("panela-500g", "Panela orgánica x 500 g", "Panela sin aditivos de trapiche artesanal.", 6200, "500 g", 30, "PROCESADOS", "rosal", "APROBADO", 27),
  product("arveja-libra", "Arveja x libra", "Arveja verde desgranada.", 5200, "libra", 15, "HORTALIZAS", "rosal", "PENDIENTE"),
  product("miel-500ml", "Miel 500 ml", "Miel pura de abejas de la Sabana.", 22000, "500 ml", 10, "PROCESADOS", "apiario", "PENDIENTE"),
  product("cuajada", "Cuajada", "Cuajada fresca de leche entera.", 9000, "unidad", 12, "LACTEOS", "pradera", "PENDIENTE"),
  product("tomate-chonto", "Tomate chonto x kg", "Publicación oculta por fotos que no corresponden.", 4800, "kg", 22, "HORTALIZAS", "rosal", "OCULTO"),
];

console.log("Productos...");
for (const p of products) await setDoc(`products/${p.id}`, p.data, token);

const byId = Object.fromEntries(products.map((p) => [p.id, p.data]));
const daysAgo = (d, h = 10) => { const t = new Date(); t.setDate(t.getDate() - d); t.setHours(h, 15, 0, 0); return t; };

function order(code, buyer, lines, created, status, ref) {
  const items = lines.map(([pid, quantity]) => ({
    productId: pid, name: byId[pid].name, unitPriceCents: byId[pid].priceCents, quantity,
    sellerId: byId[pid].sellerId, category: byId[pid].category,
  }));
  const sellerIds = [...new Set(items.map((i) => i.sellerId))];
  const subtotal = items.reduce((s, i) => s + i.unitPriceCents * i.quantity, 0);
  const shipping = sellerIds.length * 3000 * PESOS;
  return {
    code,
    data: {
      buyerId: ids[buyer].uid, buyerName: people[buyer].name, items, sellerIds,
      shippingCents: shipping, subtotalCents: subtotal, totalCents: subtotal + shipping,
      address: people[buyer].address, location: people[buyer].location, createdAt: created, status,
      payment: ref ? { provider: "ePayco", reference: ref, transactionId: `T${ref}`, response: "Aceptada", method: "VS", test: true } : null,
      paidAt: ref ? created : null,
    },
  };
}

const orders = [
  order("AS-DEMO-0001", "buyer", [["fresa-500g", 2], ["mora-500g", 1], ["queso-campesino", 1]], daysAgo(0, 9), "PAGADO", "100000001"),
  order("AS-DEMO-0002", "maria", [["fresa-500g", 1]], daysAgo(1), "PREPARANDO", "100000002"),
  order("AS-DEMO-0003", "maria", [["mora-500g", 3]], daysAgo(2), "ENVIADO", "100000003"),
  order("AS-DEMO-0004", "buyer", [["huevos-aa-30", 1]], daysAgo(3), "ENVIADO", "100000004"),
  order("AS-DEMO-0005", "buyer", [["fresa-500g", 1], ["lechuga-crespa", 2]], daysAgo(5), "ENTREGADO", "100000005"),
  order("AS-DEMO-0006", "maria", [["queso-campesino", 2]], daysAgo(4), "DISPUTA", "100000006"),
  order("AS-DEMO-0007", "buyer", [["panela-500g", 2], ["astromelias", 1]], daysAgo(6), "ENTREGADO", "100000007"),
];

console.log("Pedidos...");
for (const o of orders) await setDoc(`orders/${o.code}`, o.data, token);

await setDoc("config/categories", { inactive: [] }, token);
await setDoc("audit/seed", { adminName: "Sistema", action: "Carga inicial de datos de demostración", createdAt: new Date() }, token);

console.log("Listo. Contraseña de todas las cuentas demo:", PASSWORD);
