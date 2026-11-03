/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.neoforge;

import icyllis.arc3d.engine.Device;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.opengl.GLDevice;

import java.io.PrintWriter;
import java.util.Objects;

/** Renderer diagnostics without assuming a graphics backend. */
public final class RendererDebugInfo {
    private RendererDebugInfo() {
    }

    /** Dumps the actual device capabilities and reports whether GL texture queries are legal. */
    public static boolean dump(PrintWriter pw, Device device) {
        Objects.requireNonNull(device, "Renderer device");
        pw.println("Renderer backend: " + switch (device.getBackend()) {
            case Engine.BackendApi.kOpenGL -> "OpenGL";
            case Engine.BackendApi.kVulkan -> "Vulkan";
            default -> "Unknown (" + device.getBackend() + ")";
        });
        pw.println("Renderer device: " + device.getClass().getName());
        var caps = device.getCaps();
        var details = new StringBuilder(caps.getClass().getSimpleName() + ":\n");
        caps.dump(details, false);
        pw.println(details);
        return device instanceof GLDevice glDevice && glDevice.getCaps().hasDSASupport();
    }
}
