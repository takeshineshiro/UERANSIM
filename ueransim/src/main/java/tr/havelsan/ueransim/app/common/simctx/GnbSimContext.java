/*
 * MIT License
 *
 * Copyright (c) 2020 ALİ GÜNGÖR
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

package tr.havelsan.ueransim.app.common.simctx;

import tr.havelsan.ueransim.app.app.UeRanSim;
import tr.havelsan.ueransim.app.common.Guami;
import tr.havelsan.ueransim.app.common.configs.GnbConfig;
import tr.havelsan.ueransim.app.common.contexts.GnbAmfContext;
import tr.havelsan.ueransim.app.common.contexts.GnbUeContext;
import tr.havelsan.ueransim.app.common.contexts.GtpUContext;
import tr.havelsan.ueransim.utils.console.Logger;

import java.util.HashMap;
import java.util.UUID;

public class GnbSimContext extends BaseSimContext {
    public Logger logger;
    public GnbConfig config;

    public HashMap<Guami, GnbAmfContext> amfContexts;
    public HashMap<UUID, GnbUeContext> ueContexts;
    public long ueNgapIdCounter;

    public GtpUContext gtpUCtx;

    public GnbSimContext(UeRanSim sim) {
        super(sim);
        this.amfContexts = new HashMap<>();
        this.ueContexts = new HashMap<>();
    }
}
