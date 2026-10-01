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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RemoteServerUserInfo")
class RemoteServerUserInfoTest {

    @Test
    @DisplayName("in password mode the secret is the password, and only a password is prompted")
    void constructor_keepsPasswordInPasswordMode() {
        RemoteServerUserInfo info = new RemoteServerUserInfo("pw", true);

        assertThat(info.getPassword()).isEqualTo("pw");
        assertThat(info.getPassphrase()).isNull();
        assertThat(info.promptPassword("Password")).isTrue();
        assertThat(info.promptPassphrase("Passphrase")).isFalse();
    }

    @Test
    @DisplayName("in key mode the secret is the passphrase, and only a passphrase is prompted")
    void constructor_keepsPassphraseInKeyMode() {
        RemoteServerUserInfo info = new RemoteServerUserInfo("pw", false);

        assertThat(info.getPassphrase()).isEqualTo("pw");
        assertThat(info.getPassword()).isNull();
        assertThat(info.promptPassphrase("Passphrase")).isTrue();
        assertThat(info.promptPassword("Password")).isFalse();
    }
}
