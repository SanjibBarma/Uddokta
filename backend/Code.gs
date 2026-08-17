/**
 * Uddokta Hisab — Google Apps Script backend
 * Bind this script to the target Google Spreadsheet, run setupSystem() once,
 * add the first SUPER_ADMIN in USERS, then Deploy > Web app (Execute as me,
 * Who has access: Anyone). Never share the spreadsheet itself with normal users.
 */
const SHEETS = { USERS:'USERS', TASKS:'TASKS', ASSIGN:'ASSIGNMENTS', REQUESTS:'CHANGE_REQUESTS', SESSIONS:'SESSIONS', AUDIT:'AUDIT_LOG' };
const HEADERS = {
  USERS:['id','username','passwordHash','role','fullName','presentAddress','permanentAddress','phone','fatherPhone','nid','profileComplete','active','createdAt'],
  TASKS:['id','name','unit','active','createdAt'],
  ASSIGN:['userId','taskId','active','assignedBy','updatedAt'],
  REQUESTS:['id','recordId','userId','userName','taskId','reason','newQuantity','newUnitPrice','newNote','status','createdAt','decidedBy','decidedAt'],
  SESSIONS:['token','userId','expiresAt'], AUDIT:['id','userId','action','details','createdAt']
};
const RECORD_HEADERS=['id','userId','userName','date','quantity','unit','unitPrice','total','note','createdAt','createdBy','updatedAt'];

function doGet(e){ return output({success:true,message:'Uddokta Hisab API is online',data:{version:'1.0.0'}}); }
function doPost(e){
  try {
    const req=JSON.parse((e.postData&&e.postData.contents)||'{}');
    if(!req.action) throw new Error('Action is required');
    const lock=LockService.getScriptLock(); lock.waitLock(20000);
    try { return output(dispatch(req)); } finally { lock.releaseLock(); }
  } catch(err){ return output({success:false,message:String(err.message||err),data:null}); }
}
function dispatch(req){
  const p=req.payload||{};
  if(req.action==='initialize'){ setupSystem(); return ok('Google Sheet প্রস্তুত হয়েছে',{}); }
  if(req.action==='login') return login(p);
  const actor=authenticate(req.token);
  if(req.action!=='bootstrap' && req.action!=='completeProfile' && req.action!=='logout' && !actor.profileComplete) throw new Error('প্রথমে প্রোফাইল ১০০% সম্পন্ন করুন');
  switch(req.action){
    case 'bootstrap': return ok('ডাটা লোড হয়েছে', bootstrap(actor));
    case 'logout': deleteSession(req.token); return ok('লগআউট হয়েছে',{});
    case 'completeProfile': return completeProfile(actor,p);
    case 'addRecord': return addRecord(actor,p);
    case 'requestChange': return requestChange(actor,p);
    case 'createUser': requireAdmin(actor); return createUser(actor,p);
    case 'assignTasks': requireAdmin(actor); return assignTasks(actor,p);
    case 'decideChangeRequest': requireAdmin(actor); return decideRequest(actor,p);
    default: throw new Error('Unknown action: '+req.action);
  }
}
function setupSystem(){
  Object.keys(HEADERS).forEach(k=>ensureSheet(SHEETS[k],HEADERS[k]));
  const tasks=sheetObjects(SHEETS.TASKS);
  if(!tasks.length){
    [['TASK_SALES_PCS','দৈনিক পিস বিক্রি','pcs'],['TASK_SALES_KG','দৈনিক কেজি বিক্রি','kg'],['TASK_PURCHASE','ক্রয় হিসাব','pcs'],['TASK_EXPENSE','দৈনিক খরচ','tk']].forEach(x=>appendObject(SHEETS.TASKS,HEADERS.TASKS,{id:x[0],name:x[1],unit:x[2],active:true,createdAt:now()}));
  }
  return 'Setup complete. Add the first admin with createFirstAdmin("admin","strong-password") or add a USERS row manually.';
}
function createFirstAdmin(username,password){
  setupSystem(); if(sheetObjects(SHEETS.USERS).some(u=>u.role==='SUPER_ADMIN')) throw new Error('Super admin already exists');
  appendObject(SHEETS.USERS,HEADERS.USERS,{id:uuid(),username:normalizeUser(username),passwordHash:hashPassword(password),role:'SUPER_ADMIN',profileComplete:false,active:true,createdAt:now()});
  return 'Admin created';
}
function login(p){
  const username=normalizeUser(p.username), password=String(p.password||'');
  const users=sheetObjects(SHEETS.USERS), user=users.find(u=>normalizeUser(u.username)===username);
  if(!user || !toBool(user.active)) throw new Error('ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়');
  // A manually entered plain password is accepted once, then migrated to SHA-256.
  const stored=String(user.passwordHash||''); const looksHashed=/^[a-f0-9]{64}$/i.test(stored); const valid=stored===hashPassword(password)||(!looksHashed&&stored===password);
  if(!valid) throw new Error('ইউজারনেম অথবা পাসওয়ার্ড সঠিক নয়');
  if(!looksHashed&&stored===password) updateById(SHEETS.USERS,'id',user.id,{passwordHash:hashPassword(password)});
  purgeExpiredSessions(); const token=Utilities.getUuid().replace(/-/g,'')+Utilities.getUuid().replace(/-/g,'');
  appendObject(SHEETS.SESSIONS,HEADERS.SESSIONS,{token:token,userId:user.id,expiresAt:new Date(Date.now()+30*24*3600*1000).toISOString()});
  audit(user.id,'LOGIN',''); return ok('লগইন সফল',{token:token,user:publicUser(user)});
}
function authenticate(token){
  if(!token) throw new Error('সেশন পাওয়া যায়নি');
  const s=sheetObjects(SHEETS.SESSIONS).find(x=>x.token===token);
  if(!s || new Date(s.expiresAt).getTime()<Date.now()) throw new Error('সেশন শেষ হয়েছে। আবার লগইন করুন');
  const u=sheetObjects(SHEETS.USERS).find(x=>x.id===s.userId);
  if(!u || !toBool(u.active)) throw new Error('অ্যাকাউন্ট নিষ্ক্রিয়'); return normalizeUserObject(u);
}
function bootstrap(actor){
  const tasks=getTasks(actor); const dashboard=getDashboard(actor.id);
  const data={user:publicUser(actor),tasks:tasks,dashboard:dashboard,users:[],requests:[],assignments:[],userSummaries:[]};
  if(actor.role==='SUPER_ADMIN'){
    data.users=sheetObjects(SHEETS.USERS).filter(x=>x.id!==actor.id).map(publicUser);
    data.requests=sheetObjects(SHEETS.REQUESTS).sort((a,b)=>String(b.createdAt).localeCompare(String(a.createdAt)));
    data.assignments=sheetObjects(SHEETS.ASSIGN).filter(a=>toBool(a.active)).map(a=>({userId:a.userId,taskId:a.taskId,active:true}));
    data.userSummaries=sheetObjects(SHEETS.USERS).map(u=>({user:publicUser(u),dashboard:getDashboard(u.id)}));
  }
  return data;
}
function completeProfile(actor,p){
  if(actor.profileComplete) throw new Error('সম্পন্ন প্রোফাইল সরাসরি পরিবর্তন করা যাবে না');
  ['fullName','presentAddress','permanentAddress','phone','fatherPhone','nid'].forEach(k=>{if(!String(p[k]||'').trim()) throw new Error('সব তথ্য পূরণ করা আবশ্যক');});
  updateById(SHEETS.USERS,'id',actor.id,{fullName:clean(p.fullName),presentAddress:clean(p.presentAddress),permanentAddress:clean(p.permanentAddress),phone:clean(p.phone),fatherPhone:clean(p.fatherPhone),nid:clean(p.nid),profileComplete:true});
  audit(actor.id,'PROFILE_COMPLETED',''); return ok('প্রোফাইল সম্পন্ন হয়েছে',{});
}
function createUser(actor,p){
  const username=normalizeUser(p.username), password=String(p.password||'');
  if(username.length<3) throw new Error('ইউজারনেম কমপক্ষে ৩ অক্ষরের হতে হবে'); if(password.length<6) throw new Error('পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে');
  if(sheetObjects(SHEETS.USERS).some(u=>normalizeUser(u.username)===username)) throw new Error('এই ইউজারনেম আগে থেকেই আছে');
  appendObject(SHEETS.USERS,HEADERS.USERS,{id:uuid(),username:username,passwordHash:hashPassword(password),role:'USER',profileComplete:false,active:true,createdAt:now()});
  audit(actor.id,'CREATE_USER',username); return ok('ইউজার তৈরি হয়েছে',{});
}
function getTasks(actor){
  const all=sheetObjects(SHEETS.TASKS).filter(t=>toBool(t.active)); if(actor.role==='SUPER_ADMIN') return all.map(t=>({id:t.id,name:t.name,unit:t.unit,assigned:true}));
  const ids=sheetObjects(SHEETS.ASSIGN).filter(a=>a.userId===actor.id&&toBool(a.active)).map(a=>a.taskId);
  return all.filter(t=>ids.indexOf(t.id)>=0).map(t=>({id:t.id,name:t.name,unit:t.unit,assigned:true}));
}
function assignTasks(actor,p){
  const uid=String(p.userId||''), ids=Array.isArray(p.taskIds)?p.taskIds.map(String):[];
  if(!sheetObjects(SHEETS.USERS).some(u=>u.id===uid)) throw new Error('ইউজার পাওয়া যায়নি');
  const sh=ensureSheet(SHEETS.ASSIGN,HEADERS.ASSIGN), rows=sheetObjects(SHEETS.ASSIGN);
  rows.filter(x=>x.userId===uid).forEach(x=>updateRow(SHEETS.ASSIGN,x._row,{active:false,updatedAt:now()}));
  ids.forEach(id=>{const old=rows.find(x=>x.userId===uid&&x.taskId===id);if(old)updateRow(SHEETS.ASSIGN,old._row,{active:true,assignedBy:actor.id,updatedAt:now()});else appendObject(SHEETS.ASSIGN,HEADERS.ASSIGN,{userId:uid,taskId:id,active:true,assignedBy:actor.id,updatedAt:now()});});
  audit(actor.id,'ASSIGN_TASKS',uid+':'+ids.join(',')); return ok('কাজ অ্যাসাইন করা হয়েছে',{});
}
function addRecord(actor,p){
  const task=getTasks(actor).find(t=>t.id===String(p.taskId)); if(!task) throw new Error('এই কাজটি আপনার জন্য অ্যাসাইন করা নেই');
  const qty=Number(p.quantity), price=Number(p.unitPrice); if(!(qty>0)||price<0||!isFinite(price)) throw new Error('পরিমাণ বা মূল্য সঠিক নয়');
  const date=String(p.date||''); if(!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('তারিখ YYYY-MM-DD ফরম্যাটে দিন');
  const shName=taskSheetName(task), record={id:uuid(),userId:actor.id,userName:actor.fullName||actor.username,date:date,quantity:qty,unit:task.unit,unitPrice:price,total:qty*price,note:clean(p.note),createdAt:now(),createdBy:actor.id,updatedAt:''};
  appendObject(shName,RECORD_HEADERS,record); audit(actor.id,'ADD_RECORD',task.id+':'+record.id); return ok('বিক্রির হিসাব সংরক্ষিত হয়েছে',{});
}
function requestChange(actor,p){
  const found=findRecord(String(p.recordId||'')); if(!found||found.record.userId!==actor.id) throw new Error('নিজের হিসাব ছাড়া পরিবর্তন করা যাবে না');
  if(sheetObjects(SHEETS.REQUESTS).some(r=>r.recordId===p.recordId&&r.status==='PENDING')) throw new Error('এই হিসাবের একটি অনুরোধ অপেক্ষমাণ আছে');
  const qty=Number(p.newQuantity),price=Number(p.newUnitPrice),reason=clean(p.reason);if(!(qty>0)||price<0||!reason)throw new Error('নতুন তথ্য ও কারণ সঠিকভাবে দিন');
  appendObject(SHEETS.REQUESTS,HEADERS.REQUESTS,{id:uuid(),recordId:p.recordId,userId:actor.id,userName:actor.fullName||actor.username,taskId:found.task.id,reason:reason,newQuantity:qty,newUnitPrice:price,newNote:clean(p.newNote),status:'PENDING',createdAt:now()});
  audit(actor.id,'REQUEST_CHANGE',p.recordId); return ok('পরিবর্তনের অনুরোধ অ্যাডমিনকে পাঠানো হয়েছে',{});
}
function decideRequest(actor,p){
  const req=sheetObjects(SHEETS.REQUESTS).find(r=>r.id===String(p.requestId));if(!req||req.status!=='PENDING')throw new Error('অপেক্ষমাণ অনুরোধ পাওয়া যায়নি');
  const approve=p.approve===true||String(p.approve)==='true';
  if(approve){const found=findRecord(req.recordId);if(!found)throw new Error('মূল হিসাব পাওয়া যায়নি');updateRow(found.sheet,found.record._row,{quantity:Number(req.newQuantity),unitPrice:Number(req.newUnitPrice),total:Number(req.newQuantity)*Number(req.newUnitPrice),note:req.newNote,updatedAt:now()});}
  updateById(SHEETS.REQUESTS,'id',req.id,{status:approve?'APPROVED':'REJECTED',decidedBy:actor.id,decidedAt:now()});audit(actor.id,approve?'APPROVE_CHANGE':'REJECT_CHANGE',req.id);return ok(approve?'পরিবর্তন অনুমোদিত হয়েছে':'অনুরোধ বাতিল হয়েছে',{});
}
function getDashboard(userId){
  const today=Utilities.formatDate(new Date(),Session.getScriptTimeZone()||'Asia/Dhaka','yyyy-MM-dd'), month=today.substring(0,7);let tq=0,ts=0,mq=0,ms=0,all=[];
  sheetObjects(SHEETS.TASKS).forEach(t=>{const name=taskSheetName(t);const sh=SpreadsheetApp.getActive().getSheetByName(name);if(!sh)return;sheetObjects(name).filter(r=>r.userId===userId).forEach(r=>{r.taskId=t.id;r.taskName=t.name;r.quantity=Number(r.quantity)||0;r.unitPrice=Number(r.unitPrice)||0;r.total=Number(r.total)||0;all.push(r);if(r.date===today){tq+=r.quantity;ts+=r.total}if(String(r.date).indexOf(month)===0){mq+=r.quantity;ms+=r.total}});});
  all.sort((a,b)=>String(b.createdAt).localeCompare(String(a.createdAt))); return {todayQuantity:tq,todaySales:ts,monthQuantity:mq,monthSales:ms,recordCount:all.length,recentRecords:all.slice(0,100)};
}
function findRecord(id){for(const t of sheetObjects(SHEETS.TASKS)){const s=taskSheetName(t),sh=SpreadsheetApp.getActive().getSheetByName(s);if(!sh)continue;const r=sheetObjects(s).find(x=>x.id===id);if(r)return{record:r,task:t,sheet:s};}return null;}
function taskSheetName(t){return ('TASK_'+t.id+'_'+t.name).replace(/[\\\/?*\[\]:]/g,'_').substring(0,95);}
function requireAdmin(u){if(u.role!=='SUPER_ADMIN')throw new Error('শুধু সুপার অ্যাডমিন এই কাজ করতে পারবেন');}
function publicUser(u){u=normalizeUserObject(u);return{id:u.id,username:u.username,role:u.role,fullName:u.fullName,presentAddress:u.presentAddress,permanentAddress:u.permanentAddress,phone:u.phone,fatherPhone:u.fatherPhone,nid:u.nid,profileComplete:u.profileComplete,active:u.active};}
function normalizeUserObject(u){u.profileComplete=toBool(u.profileComplete);u.active=toBool(u.active);return u;}
function normalizeUser(s){return String(s||'').trim().toLowerCase();} function clean(s){return String(s||'').trim();} function toBool(v){return v===true||String(v).toLowerCase()==='true'||v===1;}
function hashPassword(p){const bytes=Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,String(p),Utilities.Charset.UTF_8);return bytes.map(b=>('0'+((b<0?b+256:b).toString(16))).slice(-2)).join('');}
function now(){return new Date().toISOString();} function uuid(){return Utilities.getUuid();} function ok(message,data){return{success:true,message:message,data:data};} function output(o){return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);}
function ss(){return SpreadsheetApp.getActiveSpreadsheet();}
function ensureSheet(name,headers){let sh=ss().getSheetByName(name);if(!sh)sh=ss().insertSheet(name);if(sh.getLastRow()===0){sh.getRange(1,1,1,headers.length).setValues([headers]).setFontWeight('bold').setBackground('#096B55').setFontColor('#ffffff');sh.setFrozenRows(1);}return sh;}
function sheetObjects(name){const sh=ss().getSheetByName(name);if(!sh||sh.getLastRow()<2)return[];const v=sh.getDataRange().getValues(),h=v[0].map(String);return v.slice(1).map((r,i)=>{const o={_row:i+2};h.forEach((k,j)=>o[k]=r[j]);return o;});}
function appendObject(name,headers,obj){const sh=ensureSheet(name,headers);sh.appendRow(headers.map(h=>obj[h]===undefined?'':obj[h]));}
function updateById(name,key,id,changes){const x=sheetObjects(name).find(o=>o[key]===id);if(!x)throw new Error('Data not found');updateRow(name,x._row,changes);}
function updateRow(name,row,changes){const sh=ss().getSheetByName(name),headers=sh.getRange(1,1,1,sh.getLastColumn()).getValues()[0].map(String);Object.keys(changes).forEach(k=>{const c=headers.indexOf(k);if(c>=0)sh.getRange(row,c+1).setValue(changes[k]);});}
function deleteSession(token){const sh=ss().getSheetByName(SHEETS.SESSIONS);sheetObjects(SHEETS.SESSIONS).filter(x=>x.token===token).sort((a,b)=>b._row-a._row).forEach(x=>sh.deleteRow(x._row));}
function purgeExpiredSessions(){const sh=ss().getSheetByName(SHEETS.SESSIONS);sheetObjects(SHEETS.SESSIONS).filter(x=>new Date(x.expiresAt).getTime()<Date.now()).sort((a,b)=>b._row-a._row).forEach(x=>sh.deleteRow(x._row));}
function audit(uid,action,details){appendObject(SHEETS.AUDIT,HEADERS.AUDIT,{id:uuid(),userId:uid,action:action,details:details,createdAt:now()});}
