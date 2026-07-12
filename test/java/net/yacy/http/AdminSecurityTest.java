package net.yacy.http;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;

/**
 * Unit tests for the servlet container neutral admin security decision logic,
 * especially the supported admin password hash formats.
 */
public class AdminSecurityTest {

    /**
     * Test which paths demand admin rights depending on configuration.
     */
    @Test
    public void testIsProtectedPath() {
        // pages suffixed with "_p" are always protected
        Assert.assertTrue(AdminSecurity.isProtectedPath("/Settings_p.html", false, false, true));
        Assert.assertTrue(AdminSecurity.isProtectedPath("/api/table_p.xml", false, false, true));
        // normal pages are public by default
        Assert.assertFalse(AdminSecurity.isProtectedPath("/index.html", false, false, true));
        Assert.assertFalse(AdminSecurity.isProtectedPath("/yacysearch.html", false, false, true));

        // adminForAllPages protects everything ...
        Assert.assertTrue(AdminSecurity.isProtectedPath("/index.html", true, false, true));
        // ... except the p2p and remote search interfaces ...
        Assert.assertFalse(AdminSecurity.isProtectedPath("/yacy/hello.html", true, false, true));
        Assert.assertFalse(AdminSecurity.isProtectedPath("/solr/select", true, false, true));
        // ... which are protected too in private robinson mode
        Assert.assertTrue(AdminSecurity.isProtectedPath("/yacy/hello.html", true, true, true));
        Assert.assertTrue(AdminSecurity.isProtectedPath("/solr/select", true, true, true));

        // without public search page the solr and gsa interfaces are protected
        Assert.assertTrue(AdminSecurity.isProtectedPath("/solr/select", false, false, false));
        Assert.assertTrue(AdminSecurity.isProtectedPath("/gsa/search", false, false, false));
        Assert.assertFalse(AdminSecurity.isProtectedPath("/index.html", false, false, false));
    }

    /**
     * Test the Base64 based admin password hash format MD5Hex(Base64(user:password)).
     */
    @Test
    public void testCheckAdminPasswordBase64Format() {
        final String user = "admin";
        final String pw = "secret";
        final String configHash = AdminSecurity.calcHash(user + ":" + pw);

        Assert.assertTrue(AdminSecurity.checkAdminPassword(user, configHash, "YaCy", "admin", false, pw));
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, "YaCy", "admin", false, "wrong"));
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, "YaCy", "admin", false, ""));

        // the hash itself is accepted as password, but only on recent localhost access (bin/apicall.sh)
        Assert.assertTrue(AdminSecurity.checkAdminPassword(user, configHash, "YaCy", "admin", true, configHash));
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, "YaCy", "admin", false, configHash));
    }

    /**
     * Test the "MD5:" prefixed admin password hash format MD5Hex(user:realm:password).
     */
    @Test
    public void testCheckAdminPasswordDigestFormat() {
        final String user = "admin";
        final String pw = "secret";
        final String realm = "YaCy";
        final String configHash = "MD5:" + Digest.encodeMD5Hex(user + ":" + realm + ":" + pw);

        Assert.assertTrue(AdminSecurity.checkAdminPassword(user, configHash, realm, "admin", false, pw));
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, realm, "admin", false, "wrong"));
        // the realm is part of the hash: a different realm must not verify
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, "OtherRealm", "admin", false, pw));

        // the full config hash is accepted as password for the admin user on recent localhost access (bin/apicall.sh)
        Assert.assertTrue(AdminSecurity.checkAdminPassword(user, configHash, realm, "admin", true, configHash));
        Assert.assertFalse(AdminSecurity.checkAdminPassword(user, configHash, realm, "admin", false, configHash));
        // but not for another user name
        Assert.assertFalse(AdminSecurity.checkAdminPassword("other", configHash, realm, "admin", true, configHash));
    }

    /**
     * Test the lazy localhost authorization with the config hash as Basic credential.
     */
    @Test
    public void testCheckLocalhostLazyAuth() {
        final String adminUser = "admin";
        final String configHash = "MD5:0cef3f723bbf6ec22bbb0ca4d4dfd001";
        final String validHeader = "Basic " + Base64Order.standardCoder.encodeString(adminUser + ":" + configHash);

        Assert.assertTrue(AdminSecurity.checkLocalhostLazyAuth(validHeader, adminUser, configHash));
        Assert.assertFalse(AdminSecurity.checkLocalhostLazyAuth(null, adminUser, configHash));
        Assert.assertFalse(AdminSecurity.checkLocalhostLazyAuth("Basic d3Jvbmc=", adminUser, configHash));
        Assert.assertFalse(AdminSecurity.checkLocalhostLazyAuth("Digest something", adminUser, configHash));
    }

    /**
     * Test the localhost access check including the referer host condition.
     */
    @Test
    public void testIsLocalhostAccess() {
        Assert.assertTrue(AdminSecurity.isLocalhostAccess("127.0.0.1", null));
        Assert.assertTrue(AdminSecurity.isLocalhostAccess("127.0.0.1", ""));
        Assert.assertTrue(AdminSecurity.isLocalhostAccess("127.0.0.1", "localhost"));
        Assert.assertFalse(AdminSecurity.isLocalhostAccess("192.0.2.17", null));
        // a request from localhost referred by a remote page is not a localhost access
        Assert.assertFalse(AdminSecurity.isLocalhostAccess("127.0.0.1", "example.org"));
    }

    /** Test the complete request-level policy used by the container adapter. */
    @Test
    public void testAdminAccessPolicy() {
        final String user = "admin";
        final String hash = AdminSecurity.calcHash(user + ":secret");
        final AdminSecurity.AccessPolicy localAllowed = new AdminSecurity.AccessPolicy(
                false, false, true, true, user, hash);

        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.PUBLIC,
                localAllowed.decide("/index.html", "192.0.2.1", null, null));
        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.ADMIN_REQUIRED,
                localAllowed.decide("/Settings_p.html", "192.0.2.1", null, null));
        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.LOCAL_BYPASS,
                localAllowed.decide("/Settings_p.html", "127.0.0.1", null, null));
        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.ADMIN_REQUIRED,
                localAllowed.decide("/Settings_p.html", "127.0.0.1", "https://example.org/", null));

        final AdminSecurity.AccessPolicy loginRequired = new AdminSecurity.AccessPolicy(
                false, false, true, false, user, hash);
        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.ADMIN_REQUIRED,
                loginRequired.decide("/Settings_p.html", "127.0.0.1", null, null));
        final String lazyAuth = "Basic " + Base64Order.standardCoder.encodeString(user + ":" + hash);
        Assert.assertEquals(AdminSecurity.AccessPolicy.Decision.LOCAL_BYPASS,
                loginRequired.decide("/Settings_p.html", "127.0.0.1", null, lazyAuth));
    }

    /** The credential context is request-bound and fails closed after cleanup. */
    @Test
    public void testAdminAuthenticationContext() {
        AdminSecurity.AuthenticationContext.clear();
        Assert.assertFalse(AdminSecurity.AuthenticationContext.isLocalhostRequest());
        AdminSecurity.AuthenticationContext.setSocketPeerIp("127.0.0.1");
        Assert.assertTrue(AdminSecurity.AuthenticationContext.isLocalhostRequest());
        AdminSecurity.AuthenticationContext.clear();
        Assert.assertFalse(AdminSecurity.AuthenticationContext.isLocalhostRequest());
    }
}
