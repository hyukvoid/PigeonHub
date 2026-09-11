const ch = 'ch_53cbdaa28a3a4ee893f2';
const old = new Date(Date.now() - 3600000).toISOString();
const stmts = [];
stmts.push("INSERT INTO messages (id, channel_id, seq, title, message, priority, url, created_at, expires_at, idempotency_key, request_hash, push_status, attempt_count, last_error) VALUES ('mvp1e-p1','" + ch + "',(SELECT COALESCE(MAX(seq),0)+1 FROM messages WHERE channel_id='" + ch + "'),'MVP1E Retry P1','bounded retry delivered this','high',NULL,'" + old + "',datetime('now','+7 days'),NULL,'x','pending',1,'fcm send failed: HTTP 500');");
stmts.push("INSERT INTO messages (id, channel_id, seq, title, message, priority, url, created_at, expires_at, idempotency_key, request_hash, push_status, attempt_count, fcm_message_id, last_error) VALUES ('mvp1e-p2','" + ch + "',(SELECT COALESCE(MAX(seq),0)+1 FROM messages WHERE channel_id='" + ch + "'),'MVP1E ScenarioD P2','self heal test','normal',NULL,'" + old + "',datetime('now','+7 days'),NULL,'x','pending',1,'projects/pigeonhub-b958d/messages/fake-accepted-id','" + old + "');");
stmts.push("INSERT INTO messages (id, channel_id, seq, title, message, priority, url, created_at, expires_at, idempotency_key, request_hash, push_status, attempt_count, last_error) VALUES ('mvp1e-p3','" + ch + "',(SELECT COALESCE(MAX(seq),0)+1 FROM messages WHERE channel_id='" + ch + "'),'MVP1E Maxed P3','should stay pending','normal',NULL,'" + old + "',datetime('now','+7 days'),NULL,'x','pending',5,'exhausted');");
require('fs').writeFileSync('C:/PigeonHub/worker/inject_pending.sql', stmts.join('\n'));
console.log('written', stmts.length);
