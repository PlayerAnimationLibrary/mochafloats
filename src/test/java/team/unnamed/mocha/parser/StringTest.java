/*
 * This file is part of mocha, licensed under the MIT license
 *
 * Copyright (c) 2021-2025 Unnamed Team
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
package team.unnamed.mocha.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.util.ExpressionListUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringTest {
    private static void testToString(String expr) throws IOException {
        System.out.println("expr " + expr);

        String original = ExpressionListUtils.toString(MochaEngine.createStandard().parse(expr));
        System.out.println("original " + original);

        String parsed = ExpressionListUtils.toString(MochaEngine.createStandard().parse(original));
        System.out.println("parsed " + parsed);

        System.out.flush();
        assertEquals(original, parsed);
    }

    @Test
    @DisplayName("Test toString method")
    public void test() throws IOException {
        testToString("array.my_geos[math.cos(query.anim_time * 12.3 + 41.9) * 10 + 0.6]");
        testToString("1");
        testToString("v.cowcow.friend = v.pigpig; v.pigpig->v.test.a.b.c = 1.23; v.moo = v.cowcow.friend->v.test.a.b; return v.moo.c;");

        testToString("""
                v.x = 0;
                for_each(v.pig, query.get_nearby_entities(4, 'minecraft:pig'), {
                    v.x = v.x + v.pig -> query.get_relative_block_state(0, 1, 0, 'flammable');
                });
                """);
        testToString("""
                v.x = 0;
                loop(10, {
                  (v.x > 5) ? continue;
                  v.x = v.x + 1;
                });""");

        testToString("-(10 * 5)");
    }
}
