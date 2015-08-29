/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.io.IOException;

public class TextUtils2 {

    public static CharSequence combine(CharSequence... charSequences) {

        Appendable appendable = null;

        for (CharSequence sequence : charSequences) {
            // Ensure appendable
            if (sequence instanceof Spanned) {
                if (appendable == null) {
                    appendable = new SpannableStringBuilder();
                } else if (!(appendable instanceof SpannableStringBuilder)) {
                    appendable = new SpannableStringBuilder(appendable.toString());
                }
            } else {
                if (appendable == null) {
                    appendable = new StringBuilder();
                }
            }

            try {
                appendable.append(sequence);
            } catch (IOException e) {
                // Ignore
            }
        }

        if (appendable instanceof StringBuilder) {
            return appendable.toString();
        } else {
            return (CharSequence) appendable;
        }
    }
}