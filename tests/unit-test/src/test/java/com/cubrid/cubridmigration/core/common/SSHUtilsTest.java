/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the copyright holder nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("SSHUtils")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SSHUtilsTest {

    private static final String[] KERBEROS_PROPERTIES = {
        "java.security.krb5.conf",
        "java.security.auth.login.config",
        "javax.security.auth.useSubjectCredsOnly"
    };
    private static final Map<String, String> ORIGINAL_PROPERTIES = kerberosProperties();

    @AfterEach
    void restoreKerberosProperties() {
        ORIGINAL_PROPERTIES.forEach(
                (name, value) -> {
                    if (value == null) {
                        System.clearProperty(name);
                    } else {
                        System.setProperty(name, value);
                    }
                });
    }

    private static Map<String, String> kerberosProperties() {
        Map<String, String> values = new HashMap<>();
        for (String name : KERBEROS_PROPERTIES) {
            values.put(name, System.getProperty(name));
        }
        return values;
    }

    private static SSHHostBaseInfo host(int authType) {
        SSHHostBaseInfo host = new SSHHostBaseInfo();
        host.setAuthType(authType);
        host.setUser("cmt");
        host.setHost("db.example");
        host.setPort(2222);
        host.setPassword("secret");
        return host;
    }

    private static SSHHostBaseInfo kerberosHost(Path dir) {
        SSHHostBaseInfo host = host(2);
        host.setKrbConfig(dir.resolve("krb5.conf").toString());
        host.setKrbTicket(dir.resolve("krb5cc_cmt").toString());
        return host;
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SSHUtils.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private static int checkAck(InputStream in, StringBuffer errorMsg) throws Exception {
        return (int)
                invoke(
                        "checkAck",
                        new Class<?>[] {InputStream.class, StringBuffer.class},
                        in,
                        errorMsg);
    }

    private static Session createSession(SSHHostBaseInfo host) throws Exception {
        return (Session) invoke("createSession", new Class<?>[] {SSHHostBaseInfo.class}, host);
    }

    private static void initKRBEnvironment(SSHHostBaseInfo host) throws Exception {
        invoke("initKRBEnvironment", new Class<?>[] {SSHHostBaseInfo.class}, host);
    }

    private static Object sessionField(Session session, String name) throws Exception {
        Field field = Session.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(session);
    }

    private static String password(Session session) throws Exception {
        byte[] password = (byte[]) sessionField(session, "password");
        return password == null ? null : new String(password, StandardCharsets.UTF_8);
    }

    private static List<String> identities(Session session) throws Exception {
        return ((JSch) sessionField(session, "jsch")).getIdentityNames();
    }

    @Nested
    @DisplayName("checkAck()")
    class CheckAck {

        private InputStream stream(int code, String rest) {
            byte[] tail = rest.getBytes(StandardCharsets.US_ASCII);
            byte[] bytes = new byte[tail.length + 1];
            bytes[0] = (byte) code;
            System.arraycopy(tail, 0, bytes, 1, tail.length);
            return new ByteArrayInputStream(bytes);
        }

        @Test
        @DisplayName("0 is success and nothing more is read")
        void success_returnsZero() throws Exception {
            StringBuffer errorMsg = new StringBuffer();
            InputStream in = stream(0, "X");

            assertThat(checkAck(in, errorMsg)).isZero();
            assertThat(errorMsg).isEmpty();
            assertThat(in.read()).isEqualTo('X');
        }

        @ParameterizedTest(name = "[{index}] code {0}")
        @DisplayName("an error or fatal code appends the message line, its line end included")
        @ValueSource(ints = {1, 2})
        void errorCode_appendsMessageLine(int code) throws Exception {
            StringBuffer errorMsg = new StringBuffer("earlier;");
            InputStream in = stream(code, "bad\nX");

            assertThat(checkAck(in, errorMsg)).isEqualTo(code);
            assertThat(errorMsg).hasToString("earlier;bad\n");
            assertThat(in.read()).isEqualTo('X');
        }

        @Test
        @DisplayName("any other code, such as C for a file, is returned without reading more")
        void otherCode_returnsWithoutReadingMore() throws Exception {
            StringBuffer errorMsg = new StringBuffer();
            InputStream in = stream('C', "0644");

            assertThat(checkAck(in, errorMsg)).isEqualTo('C');
            assertThat(errorMsg).isEmpty();
            assertThat(in.read()).isEqualTo('0');
        }

        @Test
        @DisplayName("the end of the stream is returned as -1")
        void endOfStream_returnsMinusOne() throws Exception {
            StringBuffer errorMsg = new StringBuffer();

            assertThat(checkAck(new ByteArrayInputStream(new byte[0]), errorMsg)).isEqualTo(-1);
            assertThat(errorMsg).isEmpty();
        }

        @Test
        @DisplayName("a message cut off before its line end keeps the read going past the end")
        void cutOffMessage_keepsReadingPastTheEnd() {
            // DEFECT: the message loop stops only at a line feed, and the -1 that read() returns
            // at the end of the stream never is one, so a cut-off message loops forever
            // - see SSHUtils.checkAck()
            StringBuffer errorMsg = new StringBuffer();
            InputStream cutOff =
                    new InputStream() {
                        private final InputStream data = stream(1, "x");
                        private int readsPastEnd;

                        @Override
                        public int read() throws IOException {
                            int next = data.read();
                            if (next == -1 && ++readsPastEnd > 100) {
                                throw new IOException("read past the end 100 times");
                            }
                            return next;
                        }
                    };

            assertThatThrownBy(() -> checkAck(cutOff, errorMsg))
                    .isInstanceOf(IOException.class)
                    .hasMessage("read past the end 100 times");
            assertThat(errorMsg).hasToString("x" + "\uFFFF".repeat(100));
        }
    }

    @Nested
    @DisplayName("createSession()")
    class CreateSession {

        @Test
        @DisplayName(
                "password login sets the address, password-only authentication and the password")
        void passwordLogin_setsPasswordAuthentication() throws Exception {
            Session session = createSession(host(0));

            assertThat(session.getUserName()).isEqualTo("cmt");
            assertThat(session.getHost()).isEqualTo("db.example");
            assertThat(session.getPort()).isEqualTo(2222);
            assertThat(session.getConfig("PreferredAuthentications")).isEqualTo("password");
            assertThat(session.getUserInfo())
                    .isInstanceOfSatisfying(
                            RemoteServerUserInfo.class,
                            info -> {
                                assertThat(info.getPassword()).isEqualTo("secret");
                                assertThat(info.getPassphrase()).isNull();
                            });
            assertThat(password(session)).isEqualTo("secret");
        }

        @Test
        @DisplayName("key login adds the private key and hands the password over as its passphrase")
        void keyLogin_addsKeyWithPassphrase(@TempDir Path dir) throws Exception {
            Path key = dir.resolve("id_ecdsa");
            KeyPair.genKeyPair(new JSch(), KeyPair.ECDSA, 256).writePrivateKey(key.toString());
            SSHHostBaseInfo host = host(1);
            host.setPrivateKeyAbsoluteFile(key.toString());
            host.setPassword("phrase");

            Session session = createSession(host);

            assertThat(identities(session)).containsExactly(key.toString());
            assertThat(session.getConfig("PreferredAuthentications"))
                    .isEqualTo(JSch.getConfig("PreferredAuthentications"));
            assertThat(session.getUserInfo())
                    .isInstanceOfSatisfying(
                            RemoteServerUserInfo.class,
                            info -> {
                                assertThat(info.getPassphrase()).isEqualTo("phrase");
                                assertThat(info.getPassword()).isNull();
                            });
            assertThat(password(session)).isNull();
        }

        @Test
        @DisplayName("Kerberos login prepares the Kerberos environment and asks for GSSAPI")
        void kerberosLogin_usesGssapi(@TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);

            Session session = createSession(host);

            assertThat(session.getConfig("PreferredAuthentications")).isEqualTo("gssapi-with-mic");
            assertThat(session.getUserInfo()).isExactlyInstanceOf(BaseJSCHUser.class);
            assertThat(password(session)).isEqualTo("secret");
            assertThat(System.getProperty("java.security.krb5.conf"))
                    .isEqualTo(host.getKrbConfig());
        }

        @ParameterizedTest(name = "[{index}] auth type {0}")
        @DisplayName("any other auth type asks for GSSAPI too but skips the Kerberos environment")
        @ValueSource(ints = {-1, 3})
        void otherAuthType_usesGssapiWithoutKerberosEnvironment(int authType, @TempDir Path dir)
                throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);
            host.setAuthType(authType);

            Session session = createSession(host);

            assertThat(session.getConfig("PreferredAuthentications")).isEqualTo("gssapi-with-mic");
            assertThat(kerberosProperties()).isEqualTo(ORIGINAL_PROPERTIES);
        }

        @Test
        @DisplayName("a private key that cannot be read gives null instead of a session")
        void unreadablePrivateKey_returnsNull(@TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = host(1);
            host.setPrivateKeyAbsoluteFile(dir.resolve("none").toString());

            assertThat(createSession(host)).isNull();
        }
    }

    @Nested
    @DisplayName("initKRBEnvironment()")
    class InitKRBEnvironment {

        @Test
        @DisplayName("a Kerberos host sets krb5.conf and turns off useSubjectCredsOnly")
        void kerberosHost_setsKrb5ConfAndSubjectCreds(@TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);

            initKRBEnvironment(host);

            assertThat(System.getProperty("java.security.krb5.conf"))
                    .isEqualTo(host.getKrbConfig());
            assertThat(System.getProperty("javax.security.auth.useSubjectCredsOnly"))
                    .isEqualTo("false");
        }

        @Test
        @DisplayName("the ticket path becomes the JAAS login config, written there when missing")
        void ticketPath_becomesLoginConfig(@TempDir Path dir) throws Exception {
            // DEFECT: the ticket cache path the dialogs fill in is set as the JAAS login config,
            // and with no file there a login config is written in its place, so the ticket file
            // and the login file get mixed up
            // - see SSHUtils.initKRBEnvironment()
            SSHHostBaseInfo host = kerberosHost(dir);

            initKRBEnvironment(host);

            assertThat(System.getProperty("java.security.auth.login.config"))
                    .isEqualTo(host.getKrbTicket());
            assertThat(
                            new String(
                                    Files.readAllBytes(Paths.get(host.getKrbTicket())),
                                    CUBRIDIOUtils.DEFAULT_CHARSET))
                    .isEqualTo(
                            CommonUtils.getGSSLoginConfigContent(PathUtils.getDefaultTicketFile())
                                    + System.lineSeparator());
        }

        @Test
        @DisplayName("an existing file at the ticket path is left as it is")
        void existingTicketFile_isLeftAsItIs(@TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);
            Path ticket = Files.write(Paths.get(host.getKrbTicket()), new byte[] {5, 0, 1});

            initKRBEnvironment(host);

            assertThat(Files.readAllBytes(ticket)).containsExactly(5, 0, 1);
        }

        @ParameterizedTest(name = "[{index}] auth type {0}")
        @DisplayName("a host that does not use Kerberos changes nothing")
        @ValueSource(ints = {0, 1, 3})
        void nonKerberosHost_changesNothing(int authType, @TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);
            host.setAuthType(authType);

            initKRBEnvironment(host);

            assertThat(kerberosProperties()).isEqualTo(ORIGINAL_PROPERTIES);
            assertThat(Paths.get(host.getKrbTicket())).doesNotExist();
        }

        @Test
        @DisplayName("a login config that cannot be written is only logged")
        void unwritableLoginConfig_isOnlyLogged(@TempDir Path dir) throws Exception {
            SSHHostBaseInfo host = kerberosHost(dir);
            host.setKrbTicket(dir.resolve("missing/krb5cc_cmt").toString());

            initKRBEnvironment(host);

            assertThat(System.getProperty("java.security.auth.login.config"))
                    .isEqualTo(host.getKrbTicket());
            assertThat(Paths.get(host.getKrbTicket())).doesNotExist();
        }
    }
}
