const DB_NAME = 'StudentPayDB';
const DB_VERSION = 1;

const dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = (event) => {
        console.error('Database error:', event.target.error);
        reject(event.target.error);
    };

    request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Students Store
        if (!db.objectStoreNames.contains('students')) {
            const studentStore = db.createObjectStore('students', { keyPath: 'id', autoIncrement: true });
            studentStore.createIndex('name', 'name', { unique: false });
        }

        // Payments Store
        if (!db.objectStoreNames.contains('payments')) {
            const paymentStore = db.createObjectStore('payments', { keyPath: 'id', autoIncrement: true });
            paymentStore.createIndex('student_id', 'student_id', { unique: false });
            // Compound index for quick lookup of specific month payment
            paymentStore.createIndex('student_year_month', ['student_id', 'year', 'month_paid_for'], { unique: true });
        }
    };

    request.onsuccess = (event) => {
        resolve(event.target.result);
    };
});

const db = {
    async getStudents() {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['students'], 'readonly');
            const store = transaction.objectStore('students');
            const request = store.getAll();
            
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    },

    async addStudent(student) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['students'], 'readwrite');
            const store = transaction.objectStore('students');
            const request = store.add(student);
            
            request.onsuccess = () => resolve(request.result); // Returns the new ID
            request.onerror = () => reject(request.error);
        });
    },

    async updateStudent(student) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['students'], 'readwrite');
            const store = transaction.objectStore('students');
            const request = store.put(student); // put updates if key exists
            
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    },

    async deleteStudent(id) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['students', 'payments'], 'readwrite');
            const studentStore = transaction.objectStore('students');
            const paymentStore = transaction.objectStore('payments');
            
            // Delete student
            studentStore.delete(id);
            
            // Delete associated payments
            // Note: In a real app we might want to use a cursor to delete all payments for this student
            // For simplicity/performance in this specific scope, we will query index and delete provided currently low volume
            const index = paymentStore.index('student_id');
            const request = index.openCursor(IDBKeyRange.only(id));
            
            request.onsuccess = (event) => {
                const cursor = event.target.result;
                if (cursor) {
                    cursor.delete();
                    cursor.continue();
                }
            };

            transaction.oncomplete = () => resolve();
            transaction.onerror = () => reject(transaction.error);
        });
    },

    async getPayments(year) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['payments'], 'readonly');
            const store = transaction.objectStore('payments');
            const request = store.getAll(); // Get all payments, we can filter in memory or use index if volume grows
            
            request.onsuccess = () => {
                const allPayments = request.result;
                // Filter by year if provided, though typically we might just want all or filter by student
                if (year) {
                    resolve(allPayments.filter(p => p.year === year));
                } else {
                    resolve(allPayments);
                }
            };
            request.onerror = () => reject(request.error);
        });
    },

    async addPayment(payment) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['payments'], 'readwrite');
            const store = transaction.objectStore('payments');
            const index = store.index('student_year_month');
            
            // Check if payment already exists
            const key = [payment.student_id, payment.year, payment.month_paid_for];
            const checkRequest = index.get(key);
            
            checkRequest.onsuccess = () => {
                if (checkRequest.result) {
                    // Already exists, update it (upsert) - always update date to now
                    const existingPayment = checkRequest.result;
                    existingPayment.date = payment.date || new Date().toISOString();
                    store.put(existingPayment);
                    resolve(existingPayment.id);
                } else {
                    // New payment, add it
                    const addRequest = store.add(payment);
                    addRequest.onsuccess = () => resolve(addRequest.result);
                    addRequest.onerror = (e) => {
                        console.warn('Payment add error:', e.target.error);
                        resolve(null); // Gracefully resolve
                    };
                }
            };
            
            checkRequest.onerror = () => {
                console.warn('Payment check error:', checkRequest.error);
                resolve(null);
            };
        });
    },

    async removePayment(studentId, year, monthPaidFor) {
        const database = await dbPromise;
        return new Promise((resolve, reject) => {
            const transaction = database.transaction(['payments'], 'readwrite');
            const store = transaction.objectStore('payments');
            const index = store.index('student_year_month');
            const request = index.getKey([studentId, year, monthPaidFor]);
            
            request.onsuccess = () => {
                const key = request.result;
                if (key) {
                    store.delete(key).onsuccess = () => resolve();
                } else {
                    resolve(); // Nothing to delete
                }
            };
            request.onerror = () => reject(request.error);
        });
    }
};
