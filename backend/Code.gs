/**
 * Uddokta Hisab — Google Apps Script Backend + Firebase Firestore Sync Engine
 *
 * আর্কিটেকচার (Firebase ➔ Google Sheet):
 * ১. Android App-এর সব Create / Read / Update / Delete প্রথমে সরাসরি Firebase Firestore-এ সম্পন্ন হয়।
 * ২. Firebase-এ ডাটা সেভ হওয়ার পর তা স্বয়ংক্রিয়ভাবে এই Apps Script-এর `syncFromFirebase` অথবা
 *    `pullAllFromFirestore()` এর মাধ্যমে Google Sheet-এর নির্দিষ্ট ট্যাবে (USERS, TASKS, ASSIGNMENTS,
 *    CHANGE_REQUESTS, SKUS, SESSIONS, AUDIT_LOG এবং TASK_* শিট) আপডেট হয়।
 */
const SHEETS = {
  USERS: 'USERS',
  TASKS: 'TASKS',
  ASSIGN: 'ASSIGNMENTS',
  REQUESTS: 'CHANGE_REQUESTS',
  SESSIONS: 'SESSIONS',
  AUDIT: 'AUDIT_LOG',
  SKUS: 'SKUS'
};

const HEADERS = {
  USERS: ['id', 'username', 'passwordHash', 'role', 'fullName', 'presentAddress', 'permanentAddress', 'phone', 'fatherPhone', 'nid', 'profileComplete', 'active', 'createdAt'],
  TASKS: ['id', 'name', 'unit', 'active', 'createdAt'],
  ASSIGN: ['userId', 'taskId', 'active', 'assignedBy', 'updatedAt'],
  REQUESTS: ['id', 'recordId', 'userId', 'userName', 'taskId', 'reason', 'newQuantity', 'newUnitPrice', 'newNote', 'status', 'createdAt', 'decidedBy', 'decidedAt'],
  SESSIONS: ['token', 'userId', 'expiresAt'],
  AUDIT: ['id', 'userId', 'action', 'details', 'createdAt'],
  SKUS: ['id', 'name', 'unit', 'totalStock', 'totalCost', 'createdBy', 'createdAt']
};

const RECORD_HEADERS = ['id', 'userId', 'userName', 'date', 'quantity', 'unit', 'unitPrice', 'total', 'note', 'createdAt', 'createdBy', 'updatedAt'];

function doGet(e) {
  return output({
    success: true,
    message: 'Uddokta Hisab Firebase-Sheet Sync API is online',
    data: { version: '2.0.0-firebase' }
  });
}

function doPost(e) {
  try {
    const req = JSON.parse((e.postData && e.postData.contents) || '{}');
    if (!req.action) throw new Error('Action is required');
    // Read-only operations (bootstrap) এর জন্য ScriptLock skip করি — cold start অনেক faster হবে
    if (req.action === 'bootstrap') return output(dispatch(req));
    const lock = LockService.getScriptLock();
    lock.waitLock(25000);
    try {
      return output(dispatch(req));
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return output({ success: false, message: String(err.message || err), data: null });
  }
}

function dispatch(req) {
  const p = req.payload || {};
  saveFirebaseConfigIfPresent(p);

  if (req.action === 'initialize') {
    setupSystem();
    return ok('Google Sheet প্রস্তুত হয়েছে', {});
  }
  if (req.action === 'syncFromFirebase') {
    return syncFromFirebase(p);
  }
  if (req.action === 'pullAllFromFirestore') {
    pullAllFromFirestore();
    return ok('Firebase থেকে সম্পূর্ণ Google Sheet সিঙ্ক সম্পন্ন হয়েছে', {});
  }
  if (req.action === 'login') return login(p);

  const actor = authenticate(req.token);
  if (req.action !== 'bootstrap' && req.action !== 'completeProfile' && req.action !== 'logout' && !actor.profileComplete) {
    throw new Error('প্রথমে প্রোফাইল ১০০% সম্পন্ন করুন');
  }

  switch (req.action) {
    case 'bootstrap': return ok('ডাটা লোড হয়েছে', bootstrap(actor));
    case 'logout': deleteSession(req.token); return ok('লগআউট হয়েছে', {});
    case 'completeProfile': return completeProfile(actor, p);
    case 'addRecord': return addRecord(actor, p);
    case 'requestChange': return requestChange(actor, p);
    case 'createUser': requireAdmin(actor); return createUser(actor, p);
    case 'assignTasks': requireAdmin(actor); return assignTasks(actor, p);
    case 'decideChangeRequest': requireAdmin(actor); return decideRequest(actor, p);
    case 'addSku': requireAdmin(actor); return addSku(actor, p);
    case 'addPurchase': requireAdmin(actor); return addPurchase(actor, p);
    case 'deleteSku': requireAdmin(actor); return deleteSku(actor, p);
    default: throw new Error('Unknown action: ' + req.action);
  }
}

// ═══════════════════════════════════════════════════════════════════════════
// 🔥 FIREBASE FIRESTORE ➔ GOOGLE SHEET SYNC ENGINE
// ═══════════════════════════════════════════════════════════════════════════

function saveFirebaseConfigIfPresent(p) {
  try {
    const props = PropertiesService.getScriptProperties();
    if (p.firebaseProjectId && p.firebaseProjectId !== 'uddokta-hisab-placeholder') {
      props.setProperty('FIREBASE_PROJECT_ID', String(p.firebaseProjectId));
    }
    if (p.firebaseApiKey && p.firebaseApiKey !== 'AIzaSyPlaceholderKeyReplaceWithYourFirebaseJson') {
      props.setProperty('FIREBASE_API_KEY', String(p.firebaseApiKey));
    }
  } catch (_) {}
}

/**
 * Firebase-এ কোনো ডাটা Create / Update / Delete হলে এই ফাংশনটি প্রথমে Firebase REST API থেকে
 * সর্বশেষ ডাটা যাচাই করে এবং Google Sheet-এর সংশ্লিষ্ট ট্যাবে সাথে সাথে আপডেট করে।
 */
function syncFromFirebase(p) {
  setupSystem();
  const op = String(p.operation || 'UPSERT').toUpperCase();
  const col = String(p.collection || '');
  const docId = String(p.docId || '');
  let doc = p.document || {};

  // যদি Firebase Project ID থাকে, তবে সরাসরি Firebase Firestore থেকে সর্বশেষ ডকুমেন্টটি ফেচ করার চেষ্টা করি
  if (op === 'UPSERT' && col && docId) {
    const remoteDoc = fetchFirestoreDocument(col, docId);
    if (remoteDoc && Object.keys(remoteDoc).length > 0) {
      doc = remoteDoc;
    }
  }

  applyCollectionSync(op, col, docId, doc);

  // যদি একই অ্যাকশনে দ্বিতীয় কোনো ডকুমেন্ট আপডেট হয়ে থাকে (যেমন: decideChangeRequest এ request + record)
  if (p.secondaryCollection && p.secondaryDocId) {
    let secDoc = p.secondaryDocument || {};
    const remoteSec = fetchFirestoreDocument(String(p.secondaryCollection), String(p.secondaryDocId));
    if (remoteSec && Object.keys(remoteSec).length > 0) {
      secDoc = remoteSec;
    }
    applyCollectionSync('UPSERT', String(p.secondaryCollection), String(p.secondaryDocId), secDoc);
  }

  // Audit log থাকলে AUDIT_LOG শিটে সংরক্ষণ করি
  if (p.audit && p.audit.id) {
    upsertByKey(SHEETS.AUDIT, HEADERS.AUDIT, 'id', p.audit.id, p.audit);
  }

  return ok('Firebase থেকে Google Sheet-এ আপডেট সম্পন্ন হয়েছে', {});
}

function applyCollectionSync(op, col, docId, doc) {
  if (!col) return;
  switch (col) {
    case 'users': {
      const id = String(doc.id || docId);
      if (op === 'DELETE') {
        deleteByKey(SHEETS.USERS, 'id', id);
      } else {
        upsertByKey(SHEETS.USERS, HEADERS.USERS, 'id', id, {
          id: id,
          username: normalizeUser(doc.username),
          passwordHash: String(doc.passwordHash || ''),
          role: String(doc.role || 'USER'),
          fullName: clean(doc.fullName),
          presentAddress: clean(doc.presentAddress),
          permanentAddress: clean(doc.permanentAddress),
          phone: clean(doc.phone),
          fatherPhone: clean(doc.fatherPhone),
          nid: clean(doc.nid),
          profileComplete: toBool(doc.profileComplete),
          active: doc.active === undefined ? true : toBool(doc.active),
          createdAt: String(doc.createdAt || now())
        });
        invalidateDashboardCache(id);
      }
      break;
    }

    case 'tasks': {
      const id = String(doc.id || docId);
      if (op === 'DELETE') {
        deleteByKey(SHEETS.TASKS, 'id', id);
      } else {
        upsertByKey(SHEETS.TASKS, HEADERS.TASKS, 'id', id, {
          id: id,
          name: clean(doc.name),
          unit: clean(doc.unit || 'pcs'),
          active: doc.active === undefined ? true : toBool(doc.active),
          createdAt: String(doc.createdAt || now())
        });
      }
      break;
    }

    case 'assignments': {
      const uid = String(doc.userId || '');
      const tid = String(doc.taskId || '');
      if (!uid || !tid) break;
      const sh = ensureSheet(SHEETS.ASSIGN, HEADERS.ASSIGN);
      const rows = sheetObjects(SHEETS.ASSIGN);
      const existing = rows.find(x => String(x.userId) === uid && String(x.taskId) === tid);
      const rowObj = {
        userId: uid,
        taskId: tid,
        active: toBool(doc.active),
        assignedBy: String(doc.assignedBy || ''),
        updatedAt: String(doc.updatedAt || now())
      };
      if (existing) {
        updateRow(SHEETS.ASSIGN, existing._row, rowObj);
      } else {
        appendObject(SHEETS.ASSIGN, HEADERS.ASSIGN, rowObj);
      }
      break;
    }

    case 'sales_records': {
      const id = String(doc.id || docId);
      const taskId = String(doc.taskId || '');
      const tasks = sheetObjects(SHEETS.TASKS);
      const task = tasks.find(t => String(t.id) === taskId) || {
        id: taskId || 'TASK_SALES',
        name: clean(doc.taskName || 'বিক্রি'),
        unit: clean(doc.unit || 'pcs')
      };
      const shName = taskSheetName(task);
      if (op === 'DELETE') {
        deleteByKey(shName, 'id', id);
      } else {
        const qty = Number(doc.quantity) || 0;
        const price = Number(doc.unitPrice) || 0;
        const recObj = {
          id: id,
          userId: String(doc.userId || ''),
          userName: clean(doc.userName),
          date: String(doc.date || ''),
          quantity: qty,
          unit: clean(doc.unit || task.unit || 'pcs'),
          unitPrice: price,
          total: Number(doc.total) || (qty * price),
          note: clean(doc.note),
          createdAt: String(doc.createdAt || now()),
          createdBy: String(doc.createdBy || doc.userId || ''),
          updatedAt: String(doc.updatedAt || '')
        };
        upsertByKey(shName, RECORD_HEADERS, 'id', id, recObj);
        const _sh = ss().getSheetByName(shName);
        const _dc = RECORD_HEADERS.indexOf('date') + 1;
        if (_sh && _dc > 0 && _sh.getLastRow() >= 2) {
          _sh.getRange(2, _dc, _sh.getLastRow() - 1, 1).setNumberFormat('@');
        }
        invalidateDashboardCache(recObj.userId);
      }
      break;
    }

    case 'change_requests': {
      const id = String(doc.id || docId);
      if (op === 'DELETE') {
        deleteByKey(SHEETS.REQUESTS, 'id', id);
      } else {
        upsertByKey(SHEETS.REQUESTS, HEADERS.REQUESTS, 'id', id, {
          id: id,
          recordId: String(doc.recordId || ''),
          userId: String(doc.userId || ''),
          userName: clean(doc.userName),
          taskId: String(doc.taskId || ''),
          reason: clean(doc.reason),
          newQuantity: Number(doc.newQuantity) || 0,
          newUnitPrice: Number(doc.newUnitPrice) || 0,
          newNote: clean(doc.newNote),
          status: String(doc.status || 'PENDING'),
          createdAt: String(doc.createdAt || now()),
          decidedBy: String(doc.decidedBy || ''),
          decidedAt: String(doc.decidedAt || '')
        });
      }
      break;
    }

    case 'skus': {
      const id = String(doc.id || docId);
      ensureSheet(SHEETS.SKUS, HEADERS.SKUS);
      if (op === 'DELETE') {
        deleteByKey(SHEETS.SKUS, 'id', id);
      } else {
        upsertByKey(SHEETS.SKUS, HEADERS.SKUS, 'id', id, {
          id: id,
          name: clean(doc.name),
          unit: clean(doc.unit || 'kg'),
          totalStock: Number(doc.totalStock) || 0,
          totalCost: Number(doc.totalCost) || 0,
          createdBy: String(doc.createdBy || ''),
          createdAt: String(doc.createdAt || now())
        });
      }
      break;
    }

    case 'sessions': {
      const token = String(doc.token || docId);
      if (op === 'DELETE') {
        deleteSession(token);
      } else {
        upsertByKey(SHEETS.SESSIONS, HEADERS.SESSIONS, 'token', token, {
          token: token,
          userId: String(doc.userId || ''),
          expiresAt: String(doc.expiresAt || '')
        });
      }
      break;
    }

    case 'audit_logs': {
      const id = String(doc.id || docId);
      if (id) {
        upsertByKey(SHEETS.AUDIT, HEADERS.AUDIT, 'id', id, {
          id: id,
          userId: String(doc.userId || ''),
          action: String(doc.action || ''),
          details: String(doc.details || ''),
          createdAt: String(doc.createdAt || now())
        });
      }
      break;
    }
  }
}

/**
 * সরাসরি Firebase Firestore REST API থেকে একটি নির্দিষ্ট ডকুমেন্ট রিড করে।
 */
function fetchFirestoreDocument(collection, docId) {
  try {
    const props = PropertiesService.getScriptProperties();
    const projectId = props.getProperty('FIREBASE_PROJECT_ID');
    const apiKey = props.getProperty('FIREBASE_API_KEY');
    if (!projectId || !collection || !docId) return null;
    let url = 'https://firestore.googleapis.com/v1/projects/' + encodeURIComponent(projectId) +
      '/databases/(default)/documents/' + encodeURIComponent(collection) + '/' + encodeURIComponent(docId);
    if (apiKey) url += '?key=' + encodeURIComponent(apiKey);
    const res = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
    if (res.getResponseCode() !== 200) return null;
    const json = JSON.parse(res.getContentText());
    return parseFirestoreDoc(json);
  } catch (_) {
    return null;
  }
}

/**
 * সরাসরি Firebase Firestore REST API থেকে পুরো কালেকশন রিড করে।
 */
function fetchFirestoreCollection(collection) {
  try {
    const props = PropertiesService.getScriptProperties();
    const projectId = props.getProperty('FIREBASE_PROJECT_ID');
    const apiKey = props.getProperty('FIREBASE_API_KEY');
    if (!projectId || !collection) return null;
    let url = 'https://firestore.googleapis.com/v1/projects/' + encodeURIComponent(projectId) +
      '/databases/(default)/documents/' + encodeURIComponent(collection) + '?pageSize=300';
    if (apiKey) url += '&key=' + encodeURIComponent(apiKey);
    const res = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
    if (res.getResponseCode() !== 200) return null;
    const json = JSON.parse(res.getContentText());
    const docs = json.documents || [];
    return docs.map(parseFirestoreDoc);
  } catch (_) {
    return null;
  }
}

function parseFirestoreDoc(doc) {
  if (!doc || !doc.fields) return null;
  const out = {};
  const parts = String(doc.name || '').split('/');
  out.id = parts[parts.length - 1];
  Object.keys(doc.fields).forEach(function (k) {
    out[k] = parseFirestoreValue(doc.fields[k]);
  });
  return out;
}

function parseFirestoreValue(v) {
  if (!v) return '';
  if (v.stringValue !== undefined) return v.stringValue;
  if (v.booleanValue !== undefined) return Boolean(v.booleanValue);
  if (v.integerValue !== undefined) return Number(v.integerValue);
  if (v.doubleValue !== undefined) return Number(v.doubleValue);
  if (v.timestampValue !== undefined) return v.timestampValue;
  if (v.nullValue !== undefined) return '';
  if (v.arrayValue !== undefined) {
    return (v.arrayValue.values || []).map(parseFirestoreValue);
  }
  if (v.mapValue !== undefined) {
    return parseFirestoreDoc({ fields: v.mapValue.fields || {} });
  }
  return '';
}

function toFirestoreFields(obj) {
  const fields = {};
  Object.keys(obj).forEach(function (k) {
    if (k === '_row') return;
    const val = obj[k];
    if (typeof val === 'boolean') {
      fields[k] = { booleanValue: val };
    } else if (typeof val === 'number' && isFinite(val)) {
      fields[k] = { doubleValue: val };
    } else {
      fields[k] = { stringValue: String(val === undefined || val === null ? '' : val) };
    }
  });
  return { fields: fields };
}

function upsertFirestoreDoc(collection, docId, obj) {
  const props = PropertiesService.getScriptProperties();
  const projectId = props.getProperty('FIREBASE_PROJECT_ID');
  const apiKey = props.getProperty('FIREBASE_API_KEY');
  if (!projectId) throw new Error('FIREBASE_PROJECT_ID সেট করা নেই। প্রথমে setFirebaseProject("YOUR_PROJECT_ID", "YOUR_API_KEY") রান করুন অথবা অ্যাপ থেকে একবার লগইন করুন।');
  let url = 'https://firestore.googleapis.com/v1/projects/' + encodeURIComponent(projectId) +
    '/databases/(default)/documents/' + encodeURIComponent(collection) + '/' + encodeURIComponent(docId);
  if (apiKey) url += '?key=' + encodeURIComponent(apiKey);
  UrlFetchApp.fetch(url, {
    method: 'patch',
    contentType: 'application/json',
    payload: JSON.stringify(toFirestoreFields(obj)),
    muteHttpExceptions: true
  });
}

/**
 * ম্যানুয়ালি Firebase Project ID ও Web API Key সেট করার হেল্পার (ঐচ্ছিক, অ্যাপ চালালে স্বয়ংক্রিয়ভাবেই সেট হয়ে যায়)
 */
function setFirebaseProject(projectId, apiKey) {
  const props = PropertiesService.getScriptProperties();
  props.setProperty('FIREBASE_PROJECT_ID', String(projectId || '').trim());
  if (apiKey) props.setProperty('FIREBASE_API_KEY', String(apiKey || '').trim());
  return 'Firebase Project configured: ' + projectId;
}

/**
 * Firebase Firestore থেকে সব কালেকশনের ডাটা টেনে Google Sheet-এর সব ট্যাবে আপডেট করে।
 */
function pullAllFromFirestore() {
  setupSystem();
  const collections = ['tasks', 'users', 'assignments', 'sales_records', 'change_requests', 'skus', 'audit_logs'];
  collections.forEach(function (col) {
    const docs = fetchFirestoreCollection(col);
    if (!docs) return;
    docs.forEach(function (d) {
      applyCollectionSync('UPSERT', col, d.id, d);
    });
    // যদি SKUS কালেকশনে কোনো আইটেম Firebase থেকে ডিলিট হয়ে থাকে, তবে Sheet থেকেও মুছে ফেলি
    if (col === 'skus') {
      const activeIds = docs.map(function (x) { return String(x.id); });
      const sheetSkus = sheetObjects(SHEETS.SKUS);
      sheetSkus.sort(function (a, b) { return b._row - a._row; }).forEach(function (s) {
        if (activeIds.indexOf(String(s.id)) < 0) {
          deleteRow(SHEETS.SKUS, s._row);
        }
      });
    }
  });
  return 'Pulled all collections from Firebase Firestore to Google Sheet.';
}

/**
 * Google Sheet-এ থাকা বর্তমান সব ডাটা এক ক্লিকে Firebase Firestore-এ পাঠানোর ফাংশন।
 */
function pushSheetToFirestore() {
  setupSystem();
  sheetObjects(SHEETS.TASKS).forEach(function (t) {
    upsertFirestoreDoc('tasks', String(t.id), {
      id: String(t.id),
      name: clean(t.name),
      unit: clean(t.unit || 'pcs'),
      active: toBool(t.active),
      createdAt: String(t.createdAt || now())
    });
  });
  sheetObjects(SHEETS.USERS).forEach(function (u) {
    const rawPass = String(u.passwordHash || '');
    const looksHashed = /^[a-f0-9]{64}$/i.test(rawPass);
    upsertFirestoreDoc('users', String(u.id), {
      id: String(u.id),
      username: normalizeUser(u.username),
      passwordHash: looksHashed ? rawPass : hashPassword(rawPass),
      role: String(u.role || 'USER'),
      fullName: clean(u.fullName),
      presentAddress: clean(u.presentAddress),
      permanentAddress: clean(u.permanentAddress),
      phone: clean(u.phone),
      fatherPhone: clean(u.fatherPhone),
      nid: clean(u.nid),
      profileComplete: toBool(u.profileComplete),
      active: toBool(u.active),
      createdAt: String(u.createdAt || now())
    });
  });
  sheetObjects(SHEETS.ASSIGN).forEach(function (a) {
    const docId = String(a.userId) + '_' + String(a.taskId);
    upsertFirestoreDoc('assignments', docId, {
      userId: String(a.userId),
      taskId: String(a.taskId),
      active: toBool(a.active),
      assignedBy: String(a.assignedBy || ''),
      updatedAt: String(a.updatedAt || now())
    });
  });
  sheetObjects(SHEETS.SKUS).forEach(function (s) {
    upsertFirestoreDoc('skus', String(s.id), {
      id: String(s.id),
      name: clean(s.name),
      unit: clean(s.unit || 'kg'),
      totalStock: Number(s.totalStock) || 0,
      totalCost: Number(s.totalCost) || 0,
      createdBy: String(s.createdBy || ''),
      createdAt: String(s.createdAt || now())
    });
  });
  sheetObjects(SHEETS.REQUESTS).forEach(function (r) {
    upsertFirestoreDoc('change_requests', String(r.id), {
      id: String(r.id),
      recordId: String(r.recordId),
      userId: String(r.userId),
      userName: clean(r.userName),
      taskId: String(r.taskId),
      reason: clean(r.reason),
      newQuantity: Number(r.newQuantity) || 0,
      newUnitPrice: Number(r.newUnitPrice) || 0,
      newNote: clean(r.newNote),
      status: String(r.status || 'PENDING'),
      createdAt: String(r.createdAt || now()),
      decidedBy: String(r.decidedBy || ''),
      decidedAt: String(r.decidedAt || '')
    });
  });
  const toDateStr = function (v) {
    if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone() || 'Asia/Dhaka', 'yyyy-MM-dd');
    return String(v || '');
  };
  sheetObjects(SHEETS.TASKS).forEach(function (t) {
    const shName = taskSheetName(t);
    const sh = ss().getSheetByName(shName);
    if (!sh) return;
    sheetObjects(shName).forEach(function (r) {
      upsertFirestoreDoc('sales_records', String(r.id), {
        id: String(r.id),
        userId: String(r.userId),
        userName: clean(r.userName),
        taskId: String(t.id),
        taskName: clean(t.name),
        date: toDateStr(r.date),
        quantity: Number(r.quantity) || 0,
        unit: clean(r.unit || t.unit || 'pcs'),
        unitPrice: Number(r.unitPrice) || 0,
        total: Number(r.total) || 0,
        note: clean(r.note),
        createdAt: String(r.createdAt || now()),
        createdBy: String(r.createdBy || r.userId || ''),
        updatedAt: String(r.updatedAt || '')
      });
    });
  });
  return 'All Google Sheet data pushed to Firebase Firestore successfully.';
}

/**
 * প্রতি ৫ মিনিটে স্বয়ংক্রিয়ভাবে Firebase থেকে Google Sheet সিঙ্ক করার টাইম-ট্রিগার চালু করে (ঐচ্ছিক)।
 */
function setupAutoSyncTrigger() {
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'pullAllFromFirestore') {
      ScriptApp.deleteTrigger(t);
    }
  });
  ScriptApp.newTrigger('pullAllFromFirestore').timeBased().everyMinutes(5).create();
  return '5-minute auto-sync trigger from Firebase to Google Sheet enabled.';
}

// ═══════════════════════════════════════════════════════════════════════════
// 📊 EXISTING CORE GOOGLE SHEET FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

function setupSystem() {
  Object.keys(HEADERS).forEach(k => ensureSheet(SHEETS[k], HEADERS[k]));
  const tasks = sheetObjects(SHEETS.TASKS);
  if (!tasks.length) {
    [
      ['TASK_SALES_PCS', 'দৈনিক পিস বিক্রি', 'pcs'],
      ['TASK_SALES_KG', 'দৈনিক কেজি বিক্রি', 'kg'],
      ['TASK_PURCHASE', 'ক্রয় হিসাব', 'pcs'],
      ['TASK_EXPENSE', 'দৈনিক খরচ', 'tk']
    ].forEach(x => appendObject(SHEETS.TASKS, HEADERS.TASKS, {
      id: x[0],
      name: x[1],
      unit: x[2],
      active: true,
      createdAt: now()
    }));
  }
  return 'Setup complete. Add the first admin with createFirstAdmin("admin","strong-password") or add a USERS row manually.';
}

function createFirstAdmin(username, password) {
  setupSystem();
  if (sheetObjects(SHEETS.USERS).some(u => u.role === 'SUPER_ADMIN')) throw new Error('Super admin already exists');
  appendObject(SHEETS.USERS, HEADERS.USERS, {
    id: uuid(),
    username: normalizeUser(username),
    passwordHash: hashPassword(password),
    role: 'SUPER_ADMIN',
    profileComplete: false,
    active: true,
    createdAt: now()
  });
  return 'Admin created';
}

function login(p) {
  const username = normalizeUser(p.username), password = String(p.password || '');
  const users = sheetObjects(SHEETS.USERS), user = users.find(u => normalizeUser(u.username) === username);
  if (!user || !toBool(user.active)) throw new Error('ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়');
  const stored = String(user.passwordHash || '');
  const looksHashed = /^[a-f0-9]{64}$/i.test(stored);
  const valid = stored === hashPassword(password) || (!looksHashed && stored === password);
  if (!valid) throw new Error('ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়');
  if (!looksHashed && stored === password) updateById(SHEETS.USERS, 'id', user.id, { passwordHash: hashPassword(password) });
  purgeExpiredSessions();
  const token = Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '');
  appendObject(SHEETS.SESSIONS, HEADERS.SESSIONS, {
    token: token,
    userId: user.id,
    expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString()
  });
  audit(user.id, 'LOGIN', '');
  return ok('লগইন সফল', { token: token, user: publicUser(user) });
}

function authenticate(token) {
  if (!token) throw new Error('সেশন পাওয়া যায়নি');
  const s = sheetObjects(SHEETS.SESSIONS).find(x => x.token === token);
  if (!s || new Date(s.expiresAt).getTime() < Date.now()) throw new Error('সেশন শেষ হয়েছে। আবার লগইন করুন');
  const u = sheetObjects(SHEETS.USERS).find(x => x.id === s.userId);
  if (!u || !toBool(u.active)) throw new Error('অ্যাকাউন্ট নিষ্ক্রিয়');
  return normalizeUserObject(u);
}

function bootstrap(actor) {
  ensureSheet(SHEETS.SKUS, HEADERS.SKUS);
  const tasks = getTasks(actor);
  const dashboard = getDashboard(actor.id);
  const data = { user: publicUser(actor), tasks: tasks, dashboard: dashboard, users: [], requests: [], assignments: [], userSummaries: [] };
  if (actor.role === 'SUPER_ADMIN') {
    data.users = sheetObjects(SHEETS.USERS).filter(x => x.id !== actor.id).map(publicUser);
    data.requests = sheetObjects(SHEETS.REQUESTS).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
    data.assignments = sheetObjects(SHEETS.ASSIGN).filter(a => toBool(a.active)).map(a => ({ userId: a.userId, taskId: a.taskId, active: true }));
    data.userSummaries = sheetObjects(SHEETS.USERS).map(u => ({ user: publicUser(u), dashboard: getDashboard(u.id) }));
    data.skus = listSkus();
  }
  return data;
}

function completeProfile(actor, p) {
  if (actor.profileComplete) throw new Error('সম্পন্ন প্রোফাইল সরাসরি পরিবর্তন করা যাবে না');
  ['fullName', 'presentAddress', 'permanentAddress', 'phone', 'fatherPhone', 'nid'].forEach(k => {
    if (!String(p[k] || '').trim()) throw new Error('সব তথ্য পূরণ করা আবশ্যক');
  });
  updateById(SHEETS.USERS, 'id', actor.id, {
    fullName: clean(p.fullName),
    presentAddress: clean(p.presentAddress),
    permanentAddress: clean(p.permanentAddress),
    phone: clean(p.phone),
    fatherPhone: clean(p.fatherPhone),
    nid: clean(p.nid),
    profileComplete: true
  });
  invalidateDashboardCache(actor.id);
  audit(actor.id, 'PROFILE_COMPLETED', '');
  return ok('প্রোফাইল সম্পন্ন হয়েছে', {});
}

function createUser(actor, p) {
  const username = normalizeUser(p.username), password = String(p.password || '');
  if (username.length < 3) throw new Error('ইউজারনেম কমপক্ষে ৩ অক্ষরের হতে হবে');
  if (password.length < 6) throw new Error('পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে');
  if (sheetObjects(SHEETS.USERS).some(u => normalizeUser(u.username) === username)) throw new Error('এই ইউজারনেম আগে থেকেই আছে');
  appendObject(SHEETS.USERS, HEADERS.USERS, {
    id: uuid(),
    username: username,
    passwordHash: hashPassword(password),
    role: 'USER',
    profileComplete: false,
    active: true,
    createdAt: now()
  });
  audit(actor.id, 'CREATE_USER', username);
  return ok('ইউজার তৈরি হয়েছে', {});
}

function getTasks(actor) {
  const all = sheetObjects(SHEETS.TASKS).filter(t => toBool(t.active));
  if (actor.role === 'SUPER_ADMIN') return all.map(t => ({ id: t.id, name: t.name, unit: t.unit, assigned: true }));
  const ids = sheetObjects(SHEETS.ASSIGN).filter(a => a.userId === actor.id && toBool(a.active)).map(a => a.taskId);
  return all.filter(t => ids.indexOf(t.id) >= 0).map(t => ({ id: t.id, name: t.name, unit: t.unit, assigned: true }));
}

function assignTasks(actor, p) {
  const uid = String(p.userId || ''), ids = Array.isArray(p.taskIds) ? p.taskIds.map(String) : [];
  if (!sheetObjects(SHEETS.USERS).some(u => u.id === uid)) throw new Error('ইউজার পাওয়া যায়নি');
  ensureSheet(SHEETS.ASSIGN, HEADERS.ASSIGN);
  const rows = sheetObjects(SHEETS.ASSIGN);
  rows.filter(x => x.userId === uid).forEach(x => updateRow(SHEETS.ASSIGN, x._row, { active: false, updatedAt: now() }));
  ids.forEach(id => {
    const old = rows.find(x => x.userId === uid && x.taskId === id);
    if (old) updateRow(SHEETS.ASSIGN, old._row, { active: true, assignedBy: actor.id, updatedAt: now() });
    else appendObject(SHEETS.ASSIGN, HEADERS.ASSIGN, { userId: uid, taskId: id, active: true, assignedBy: actor.id, updatedAt: now() });
  });
  audit(actor.id, 'ASSIGN_TASKS', uid + ':' + ids.join(','));
  return ok('কাজ অ্যাসাইন করা হয়েছে', {});
}

function addRecord(actor, p) {
  const task = getTasks(actor).find(t => t.id === String(p.taskId));
  if (!task) throw new Error('এই কাজটি আপনার জন্য অ্যাসাইন করা নেই');
  const qty = Number(p.quantity), price = Number(p.unitPrice);
  if (!(qty > 0) || price < 0 || !isFinite(price)) throw new Error('পরিমাণ বা মূল্য সঠিক নয়');
  const date = String(p.date || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('তারিখ YYYY-MM-DD ফরম্যাটে দিন');
  const shName = taskSheetName(task), record = {
    id: uuid(),
    userId: actor.id,
    userName: actor.fullName || actor.username,
    date: date,
    quantity: qty,
    unit: task.unit,
    unitPrice: price,
    total: qty * price,
    note: clean(p.note),
    createdAt: now(),
    createdBy: actor.id,
    updatedAt: ''
  };
  appendObject(shName, RECORD_HEADERS, record);
  const _sh = ss().getSheetByName(shName);
  const _dc = RECORD_HEADERS.indexOf('date') + 1;
  if (_dc > 0) _sh.getRange(_sh.getLastRow(), _dc).setNumberFormat('@');
  invalidateDashboardCache(actor.id);
  audit(actor.id, 'ADD_RECORD', task.id + ':' + record.id);
  return ok('বিক্রির হিসাব সংরক্ষিত হয়েছে', {});
}

function requestChange(actor, p) {
  const found = findRecord(String(p.recordId || ''));
  if (!found || found.record.userId !== actor.id) throw new Error('নিজের হিসাব ছাড়া পরিবর্তন করা যাবে না');
  if (sheetObjects(SHEETS.REQUESTS).some(r => r.recordId === p.recordId && r.status === 'PENDING')) {
    throw new Error('এই হিসাবের একটি অনুরোধ অপেক্ষমাণ আছে');
  }
  const qty = Number(p.newQuantity), price = Number(p.newUnitPrice), reason = clean(p.reason);
  if (!(qty > 0) || price < 0 || !reason) throw new Error('নতুন তথ্য ও কারণ সঠিকভাবে দিন');
  appendObject(SHEETS.REQUESTS, HEADERS.REQUESTS, {
    id: uuid(),
    recordId: p.recordId,
    userId: actor.id,
    userName: actor.fullName || actor.username,
    taskId: found.task.id,
    reason: reason,
    newQuantity: qty,
    newUnitPrice: price,
    newNote: clean(p.newNote),
    status: 'PENDING',
    createdAt: now()
  });
  audit(actor.id, 'REQUEST_CHANGE', p.recordId);
  return ok('পরিবর্তনের অনুরোধ অ্যাডমিনকে পাঠানো হয়েছে', {});
}

function decideRequest(actor, p) {
  const req = sheetObjects(SHEETS.REQUESTS).find(r => r.id === String(p.requestId));
  if (!req || req.status !== 'PENDING') throw new Error('অপেক্ষমাণ অনুরোধ পাওয়া যায়নি');
  const approve = p.approve === true || String(p.approve) === 'true';
  if (approve) {
    const found = findRecord(req.recordId);
    if (!found) throw new Error('মূল হিসাব পাওয়া যায়নি');
    updateRow(found.sheet, found.record._row, {
      quantity: Number(req.newQuantity),
      unitPrice: Number(req.newUnitPrice),
      total: Number(req.newQuantity) * Number(req.newUnitPrice),
      note: req.newNote,
      updatedAt: now()
    });
    invalidateDashboardCache(found.record.userId);
  }
  updateById(SHEETS.REQUESTS, 'id', req.id, {
    status: approve ? 'APPROVED' : 'REJECTED',
    decidedBy: actor.id,
    decidedAt: now()
  });
  audit(actor.id, approve ? 'APPROVE_CHANGE' : 'REJECT_CHANGE', req.id);
  return ok(approve ? 'পরিবর্তন অনুমোদিত হয়েছে' : 'অনুরোধ বাতিল হয়েছে', {});
}

function getDashboard(userId) {
  const today = Utilities.formatDate(new Date(), Session.getScriptTimeZone() || 'Asia/Dhaka', 'yyyy-MM-dd'), month = today.substring(0, 7);
  const cache = CacheService.getScriptCache();
  const cacheKey = 'dashboard_' + userId + '_' + today;
  const cached = cache.get(cacheKey);
  if (cached) {
    try { return JSON.parse(cached); } catch (_) {}
  }
  let tq = 0, ts = 0, mq = 0, ms = 0, all = [];
  const toDateStr = (v) => {
    if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone() || 'Asia/Dhaka', 'yyyy-MM-dd');
    return String(v || '');
  };
  sheetObjects(SHEETS.TASKS).forEach(t => {
    const name = taskSheetName(t);
    const sh = SpreadsheetApp.getActive().getSheetByName(name);
    if (!sh) return;
    sheetObjects(name).filter(r => r.userId === userId).forEach(r => {
      r.taskId = t.id;
      r.taskName = t.name;
      r.quantity = Number(r.quantity) || 0;
      r.unitPrice = Number(r.unitPrice) || 0;
      r.total = Number(r.total) || 0;
      r.date = toDateStr(r.date);
      all.push(r);
      if (r.date === today) { tq += r.quantity; ts += r.total; }
      if (r.date.indexOf(month) === 0) { mq += r.quantity; ms += r.total; }
    });
  });
  all.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  const result = { todayQuantity: tq, todaySales: ts, monthQuantity: mq, monthSales: ms, recordCount: all.length, recentRecords: all.slice(0, 100) };
  try { cache.put(cacheKey, JSON.stringify(result), 60); } catch (_) {}
  return result;
}

function invalidateDashboardCache(userId) {
  try {
    const today = Utilities.formatDate(new Date(), Session.getScriptTimeZone() || 'Asia/Dhaka', 'yyyy-MM-dd');
    CacheService.getScriptCache().remove('dashboard_' + userId + '_' + today);
  } catch (_) {}
}

function findRecord(id) {
  for (const t of sheetObjects(SHEETS.TASKS)) {
    const s = taskSheetName(t), sh = SpreadsheetApp.getActive().getSheetByName(s);
    if (!sh) continue;
    const r = sheetObjects(s).find(x => x.id === id);
    if (r) return { record: r, task: t, sheet: s };
  }
  return null;
}

function taskSheetName(t) {
  return ('TASK_' + t.id + '_' + t.name).replace(/[\\\/?*\[\]:]/g, '_').substring(0, 95);
}

function requireAdmin(u) {
  if (u.role !== 'SUPER_ADMIN') throw new Error('শুধু সুপার অ্যাডমিন এই কাজ করতে পারবেন');
}

function listSkus() {
  const skus = sheetObjects(SHEETS.SKUS);
  const statsByUnit = {};
  sheetObjects(SHEETS.TASKS).forEach(function (t) {
    const sh = SpreadsheetApp.getActive().getSheetByName(taskSheetName(t));
    if (!sh) return;
    sheetObjects(taskSheetName(t)).forEach(function (r) {
      const u = String(r.unit || '');
      if (!statsByUnit[u]) statsByUnit[u] = { sold: 0, revenue: 0 };
      statsByUnit[u].sold += Number(r.quantity) || 0;
      statsByUnit[u].revenue += Number(r.total) || 0;
    });
  });
  return skus.map(function (s) {
    const totalStock = Number(s.totalStock) || 0;
    const totalCost = Number(s.totalCost) || 0;
    const stats = statsByUnit[s.unit] || { sold: 0, revenue: 0 };
    const totalSold = stats.sold;
    const totalRevenue = stats.revenue;
    const remaining = Math.max(0, totalStock - totalSold);
    const profit = totalRevenue - totalCost;
    return {
      id: s.id, name: s.name, unit: s.unit,
      totalStock: totalStock, totalSold: totalSold, remaining: remaining,
      totalCost: totalCost, totalRevenue: totalRevenue, profit: profit,
      createdBy: s.createdBy, createdAt: s.createdAt
    };
  });
}

function addSku(actor, p) {
  const name = clean(p.name || ''), unit = clean(p.unit || 'kg');
  const totalStock = Number(p.totalStock) || 0;
  const totalCost = Number(p.totalCost) || 0;
  if (name.length < 1) throw new Error('SKU নাম দিন');
  if (totalStock < 0) throw new Error('স্টক সংখ্যা ০ বা তার বেশি হতে হবে');
  ensureSheet(SHEETS.SKUS, HEADERS.SKUS);
  const skus = sheetObjects(SHEETS.SKUS);
  if (skus.some(s => normalizeUser(s.name) === normalizeUser(name))) throw new Error('এই SKU আগে থেকেই আছে');
  const sku = { id: uuid(), name: name, unit: unit, totalStock: totalStock, totalCost: totalCost, createdBy: actor.id, createdAt: now() };
  appendObject(SHEETS.SKUS, HEADERS.SKUS, sku);
  audit(actor.id, 'ADD_SKU', name + ':' + totalStock);
  return ok('SKU যোগ হয়েছে', { sku: sku });
}

function deleteSku(actor, p) {
  const id = String(p.id || '');
  if (!id) throw new Error('SKU আইডি দিন');
  const rows = sheetObjects(SHEETS.SKUS);
  const row = rows.find(s => s.id === id);
  if (!row) throw new Error('SKU পাওয়া যায়নি');
  deleteRow(SHEETS.SKUS, row._row);
  audit(actor.id, 'DELETE_SKU', id);
  return ok('SKU মুছে ফেলা হয়েছে', {});
}

function addPurchase(actor, p) {
  const skuId = String(p.skuId || '');
  const quantity = Number(p.quantity) || 0;
  const cost = Number(p.cost) || 0;
  if (!skuId) throw new Error('SKU আইডি দিন');
  if (quantity <= 0) throw new Error('পরিমাণ ০ এর বেশি হতে হবে');
  const rows = sheetObjects(SHEETS.SKUS);
  const row = rows.find(s => s.id === skuId);
  if (!row) throw new Error('SKU পাওয়া যায়নি');
  const newStock = (Number(row.totalStock) || 0) + quantity;
  const newCost = (Number(row.totalCost) || 0) + cost;
  updateById(SHEETS.SKUS, 'id', skuId, { totalStock: newStock, totalCost: newCost });
  audit(actor.id, 'ADD_PURCHASE', skuId + ':+' + quantity + ':' + cost);
  return ok('কেনা যোগ হয়েছে', { totalStock: newStock, totalCost: newCost });
}

function publicUser(u) {
  u = normalizeUserObject(u);
  return {
    id: u.id, username: u.username, role: u.role, fullName: u.fullName,
    presentAddress: u.presentAddress, permanentAddress: u.permanentAddress,
    phone: u.phone, fatherPhone: u.fatherPhone, nid: u.nid,
    profileComplete: u.profileComplete, active: u.active
  };
}

function normalizeUserObject(u) { u.profileComplete = toBool(u.profileComplete); u.active = toBool(u.active); return u; }
function normalizeUser(s) { return String(s || '').trim().toLowerCase(); }
function clean(s) { return String(s || '').trim(); }
function toBool(v) { return v === true || String(v).toLowerCase() === 'true' || v === 1; }
function hashPassword(p) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(p), Utilities.Charset.UTF_8);
  return bytes.map(b => ('0' + ((b < 0 ? b + 256 : b).toString(16))).slice(-2)).join('');
}
function now() { return new Date().toISOString(); }
function uuid() { return Utilities.getUuid(); }
function ok(message, data) { return { success: true, message: message, data: data }; }
function output(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
function ss() { return SpreadsheetApp.getActiveSpreadsheet(); }

function ensureSheet(name, headers) {
  let sh = ss().getSheetByName(name);
  if (!sh) sh = ss().insertSheet(name);
  if (sh.getLastRow() === 0) {
    sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold').setBackground('#096B55').setFontColor('#ffffff');
    sh.setFrozenRows(1);
  }
  return sh;
}

function sheetObjects(name) {
  const sh = ss().getSheetByName(name);
  if (!sh || sh.getLastRow() < 2) return [];
  const v = sh.getDataRange().getValues(), h = v[0].map(String);
  return v.slice(1).map((r, i) => {
    const o = { _row: i + 2 };
    h.forEach((k, j) => o[k] = r[j]);
    return o;
  });
}

function appendObject(name, headers, obj) {
  const sh = ensureSheet(name, headers);
  sh.appendRow(headers.map(h => obj[h] === undefined ? '' : obj[h]));
}

function upsertByKey(name, headers, key, val, obj) {
  ensureSheet(name, headers);
  const existing = sheetObjects(name).find(o => String(o[key]) === String(val));
  if (existing) {
    updateRow(name, existing._row, obj);
  } else {
    appendObject(name, headers, obj);
  }
}

function deleteByKey(name, key, val) {
  const sh = ss().getSheetByName(name);
  if (!sh) return;
  sheetObjects(name)
    .filter(o => String(o[key]) === String(val))
    .sort((a, b) => b._row - a._row)
    .forEach(o => sh.deleteRow(o._row));
}

function deleteRow(name, row) {
  const sh = ss().getSheetByName(name);
  if (sh && row >= 2) sh.deleteRow(row);
}

function updateById(name, key, id, changes) {
  const x = sheetObjects(name).find(o => o[key] === id);
  if (!x) throw new Error('Data not found');
  updateRow(name, x._row, changes);
}

function updateRow(name, row, changes) {
  const sh = ss().getSheetByName(name), headers = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].map(String);
  Object.keys(changes).forEach(k => {
    const c = headers.indexOf(k);
    if (c >= 0) sh.getRange(row, c + 1).setValue(changes[k]);
  });
}

function deleteSession(token) {
  const sh = ss().getSheetByName(SHEETS.SESSIONS);
  if (!sh) return;
  sheetObjects(SHEETS.SESSIONS).filter(x => x.token === token).sort((a, b) => b._row - a._row).forEach(x => sh.deleteRow(x._row));
}

function purgeExpiredSessions() {
  const sh = ss().getSheetByName(SHEETS.SESSIONS);
  if (!sh) return;
  sheetObjects(SHEETS.SESSIONS).filter(x => new Date(x.expiresAt).getTime() < Date.now()).sort((a, b) => b._row - a._row).forEach(x => sh.deleteRow(x._row));
}

function audit(uid, action, details) {
  appendObject(SHEETS.AUDIT, HEADERS.AUDIT, { id: uuid(), userId: uid, action: action, details: details, createdAt: now() });
}
