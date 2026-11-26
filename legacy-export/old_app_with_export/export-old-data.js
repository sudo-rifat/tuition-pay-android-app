/*
 * Student Pay Manager - export of the OLD web app's data (IndexedDB "StudentPayDB").
 *
 * Reads the `students` and `payments` stores exactly as the old app stored them and downloads one
 * JSON file that the new Android app can import (Settings > Restore backup).
 * It only READS. It never modifies or deletes anything in the old app.
 *
 * Usage (must run on the SAME site/origin where the old app runs):
 *   - Paste this whole file into the browser console of the old app, or
 *   - use the patched copy of the old app (old_app_with_export/), which adds an "Export data" button.
 */
(function (root) {
    'use strict';

    function openDb(indexedDBImpl) {
        return new Promise(function (resolve, reject) {
            // No version argument: open whatever version exists (the old app used version 1).
            var req = indexedDBImpl.open('StudentPayDB');
            req.onerror = function () { reject(req.error); };
            req.onsuccess = function () { resolve(req.result); };
            req.onupgradeneeded = function () {
                // Database did not exist: abort so we do not create an empty one.
                req.transaction.abort();
                reject(new Error('StudentPayDB was not found on this site.'));
            };
        });
    }

    function readAll(db, storeName) {
        return new Promise(function (resolve, reject) {
            if (!db.objectStoreNames.contains(storeName)) { resolve([]); return; }
            var req = db.transaction([storeName], 'readonly').objectStore(storeName).getAll();
            req.onsuccess = function () { resolve(req.result); };
            req.onerror = function () { reject(req.error); };
        });
    }

    async function buildBackup(indexedDBImpl) {
        var db = await openDb(indexedDBImpl);
        try {
            var students = await readAll(db, 'students');
            var payments = await readAll(db, 'payments');
            return {
                app: 'StudentPayManager',
                formatVersion: 1,
                source: 'legacy-pwa',
                exportedAt: new Date().toISOString(),
                students: students,
                payments: payments
            };
        } finally {
            db.close();
        }
    }

    async function exportOldData() {
        var backup = await buildBackup(root.indexedDB);
        var blob = new Blob([JSON.stringify(backup, null, 1)], { type: 'application/json' });
        var a = root.document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'student-pay-backup-' + new Date().toISOString().slice(0, 10) + '.json';
        root.document.body.appendChild(a);
        a.click();
        a.remove();
        root.alert('Exported ' + backup.students.length + ' students and ' + backup.payments.length +
            ' payments.\nNow open the new app: Settings > Restore backup.');
        return backup;
    }

    root.exportOldData = exportOldData;
    root.__buildBackupForTest = buildBackup;
    if (typeof module !== 'undefined' && module.exports) { module.exports = { buildBackup: buildBackup }; }
})(typeof window !== 'undefined' ? window : globalThis);

// When pasted into the console, run immediately.
if (typeof window !== 'undefined' && typeof window.document !== 'undefined' && !window.__SPM_NO_AUTORUN) {
    window.exportOldData().catch(function (e) { alert('Export failed: ' + e.message); });
}
