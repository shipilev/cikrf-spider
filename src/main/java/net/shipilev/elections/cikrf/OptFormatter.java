/**
 Copyright 2012 Aleksey Shipilev

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package net.shipilev.elections.cikrf;

import joptsimple.HelpFormatter;
import joptsimple.OptionDescriptor;

import java.util.Map;

public class OptFormatter implements HelpFormatter {

    public String format(Map<String, ? extends OptionDescriptor> options) {
        StringBuilder buffer = new StringBuilder();

        buffer.append("Required options are:\n");
        for (OptionDescriptor each : options.values()) {
            if (each.isRequired()) {
                buffer.append(lineFor(each));
            }
        }
        buffer.append("\n");

        buffer.append("Optionals are:\n");
        for (OptionDescriptor each : options.values()) {
            if (!each.isRequired()) {
                buffer.append(lineFor(each));
            }
        }
        return buffer.toString();
    }

    private String lineFor(OptionDescriptor descriptor) {
        StringBuilder line = new StringBuilder();

        StringBuilder optionList = new StringBuilder();
        optionList.append("  ");
        for (String str : descriptor.options()) {
            optionList.append("-").append(str);
            if (descriptor.acceptsArguments()) {
                optionList.append(" <").append(descriptor.argumentDescription()).append(">");
            }
        }

        line.append(String.format("%-30s", optionList.toString()));
        line.append(" ").append(descriptor.description());

        if (descriptor.defaultValues().size() > 0) {
            line.append(" (default: ");
            for (Object obj : descriptor.defaultValues()) {
                line.append(obj.toString());
            }
            line.append(")");
        }

        line.append(System.getProperty("line.separator"));
        return line.toString();
    }

}
