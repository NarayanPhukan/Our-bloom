const { getAuth, getFirestore } = require('../utils/firebase');

/**
 * Middleware to enforce strict administrative authorization.
 * 
 * Hierarchy:
 * 1. Dedicated X-Admin-Key header matching ADMIN_SECRET_KEY (System/Devops)
 * 2. Cryptographically verified Firebase Admin ID Token with Custom Claims (Authoritative)
 * 3. Fallback check against Firestore admin_users document (Metadata/Audit during migration)
 * 
 * Never relies on hardcoded UIDs or phone numbers.
 */
async function adminAuthMiddleware(req, res, next) {
  try {
    const adminKeyHeader = req.headers['x-admin-key'];
    const expectedAdminKey = process.env.ADMIN_SECRET_KEY;

    // 1. System Admin Key check (e.g. CLI, CI/CD, DevOps maintenance)
    if (expectedAdminKey && adminKeyHeader && adminKeyHeader === expectedAdminKey) {
      req.adminUser = {
        uid: 'system-admin',
        role: 'super_admin',
        email: 'system@ourbloom.app'
      };
      return next();
    }

    // 2. Bearer token verification via Firebase Auth
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Authentication required: missing administrative Bearer token' });
    }

    const token = authHeader.split(' ')[1];
    const auth = getAuth();
    if (!auth) {
      return res.status(500).json({ error: 'Authentication service unavailable: Firebase Auth not initialized' });
    }

    let decodedToken;
    try {
      decodedToken = await auth.verifyIdToken(token);
    } catch (tokenErr) {
      return res.status(401).json({ error: 'Invalid or expired administrative token' });
    }

    const uid = decodedToken.uid;
    let isAuthorizedAdmin = false;
    let adminRole = decodedToken.role || 'moderator';

    // A. Authoritative Firebase custom claim
    if (decodedToken.admin === true) {
      isAuthorizedAdmin = true;
      adminRole = decodedToken.role || 'super_admin';
    }

    // B. Fallback to Firestore admin_users collection for metadata validation
    if (!isAuthorizedAdmin) {
      const db = getFirestore();
      if (db) {
        const adminDoc = await db.collection('admin_users').doc(uid).get();
        if (adminDoc.exists) {
          const adminData = adminDoc.data();
          if (adminData.active !== false) {
            isAuthorizedAdmin = true;
            adminRole = adminData.role || 'support_admin';
          }
        }
      }
    }

    if (!isAuthorizedAdmin) {
      return res.status(403).json({ error: 'Forbidden: administrative privileges required' });
    }

    req.adminUser = {
      uid: uid,
      role: adminRole,
      email: decodedToken.email || '',
      claims: decodedToken
    };

    next();
  } catch (err) {
    console.error('✿ Admin auth error:', err.message);
    res.status(500).json({ error: 'Internal authorization error' });
  }
}

/**
 * Role-gated middleware factory.
 * Example: requireAdminRole(['finance_admin', 'super_admin'])
 */
function requireAdminRole(allowedRoles = []) {
  return (req, res, next) => {
    adminAuthMiddleware(req, res, () => {
      const currentRole = req.adminUser?.role;
      if (currentRole === 'super_admin' || allowedRoles.length === 0 || allowedRoles.includes(currentRole)) {
        return next();
      }
      return res.status(403).json({
        error: `Forbidden: role '${currentRole}' is not authorized for this financial/administrative action. Requires one of: [${allowedRoles.join(', ')}]`
      });
    });
  };
}

module.exports = adminAuthMiddleware;
module.exports.requireAdminRole = requireAdminRole;
