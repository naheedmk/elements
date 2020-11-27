/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.security.hsm;

import net.e6tech.elements.common.util.StringUtil;
import net.e6tech.elements.security.Hex;

/**
 * Created by futeh.
 */
public class AnsiPinBlock {
    byte[] encoding;
    String pin;
    boolean sanityCheck = true;

    public AnsiPinBlock(String partialPan, String pin) {
        if (partialPan.length() != 12)
            throw new IllegalArgumentException("invalid partial pan length, must be 12");
        if (pin == null)
            throw new IllegalArgumentException("null pin");
        if (pin.length() < 4 || pin.length() > 12)
            throw new IllegalArgumentException("invalid pin length.");
        int pinLen = pin.length();
        String pinBlock = "0" + Hex.toNumeric(pinLen) + pin;
        pinBlock = StringUtil.padRight(pinBlock, 16, 'F');
        byte[] pinBytes = Hex.toBytes(pinBlock);
        byte[] panBytes = Hex.toBytes("0000" + partialPan);
        byte[] xor = new byte[8];
        for (int i = 0; i < 8; i++)
            xor[i] = (byte)(pinBytes[i] ^ panBytes[i]);
        encoding = xor;
        this.pin = pin;
    }

    public AnsiPinBlock(byte[] encoding, String partialPan) {
        if (partialPan.length() != 12)
            throw new IllegalArgumentException("invalid partial pan length, must be 12");
        this.encoding = encoding;
        byte[] panBytes = Hex.toBytes("0000" + partialPan);
        byte[] pinBytes = new byte[8];
        for (int i = 0; i < 8; i++)
            pinBytes[i] = (byte)(encoding[i] ^ panBytes[i]);
        String pinStr = Hex.toString(pinBytes);
        int pinLen;
        try {
            pinLen = Integer.parseInt(pinStr.substring(1, 2));
        } catch (NumberFormatException e) {
            sanityCheck = false;
            // set pin and encoding to non-null values
            pin = "";
            encoding = new byte[0];
            return;
        }
        pin = pinStr.substring(2, pinLen + 2);
        String leftOver = pinStr.substring(pinLen + 2);
        for (int i = 0; i < leftOver.length(); i++) {
            char ch = leftOver.charAt(i);
            if (ch != 'F') {
                sanityCheck = false;
                break;
            }
        }
    }

    public boolean isSanityCheck() {
        return sanityCheck;
    }

    public String getPIN() {
        return pin;
    }

    public byte[] getEncoding() {
        return encoding;
    }

}
