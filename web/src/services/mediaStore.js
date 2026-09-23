const databaseName = 'ai-interview-media';
const storeName = 'answers';

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(storeName, { keyPath: 'id' });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function saveMedia(id, blob) {
  const db = await openDatabase();
  await new Promise((resolve, reject) => {
    const request = db.transaction(storeName, 'readwrite').objectStore(storeName).put({ id, blob });
    request.onsuccess = resolve;
    request.onerror = () => reject(request.error);
  });
  db.close();
}

export async function getMedia(id) {
  const db = await openDatabase();
  const result = await new Promise((resolve, reject) => {
    const request = db.transaction(storeName).objectStore(storeName).get(id);
    request.onsuccess = () => resolve(request.result?.blob || null);
    request.onerror = () => reject(request.error);
  });
  db.close();
  return result;
}

export async function deleteMedia(ids) {
  const db = await openDatabase();
  await new Promise((resolve, reject) => {
    const transaction = db.transaction(storeName, 'readwrite');
    ids.forEach((id) => transaction.objectStore(storeName).delete(id));
    transaction.oncomplete = resolve;
    transaction.onerror = () => reject(transaction.error);
  });
  db.close();
}
