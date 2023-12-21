/*
 * This file is part of molang, licensed under the MIT license
 *
 * Copyright (c) 2021-2023 Unnamed Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package team.unnamed.molang;

import org.junit.jupiter.api.Test;
import team.unnamed.molang.runtime.Function;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForEachTest {
    @Test
    void test() throws Exception {
        final MolangEngine<?> engine = MolangEngine.create();
        engine.bindDefaults();
        engine.bindVariable("list_people", (Function) (ctx, args) -> Arrays.asList("Andre", "John", "Ian", "Salva"));

        Object result;
        try (Reader reader = new InputStreamReader(ForEachTest.class.getClassLoader().getResourceAsStream("for_each.molang"))) {
            result = engine.eval(reader);
        }
        assertEquals("Andre, John, Ian, Salva", result);
    }
}
