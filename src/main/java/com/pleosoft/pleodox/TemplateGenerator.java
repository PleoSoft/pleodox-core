/**
 * Copyright 2019 Pleo Soft d.o.o. (pleosoft.com)

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pleosoft.pleodox;

import java.io.InputStream;
import java.io.OutputStream;

import com.pleosoft.pleodox.data.PleodoxRoot.PleodoxRequest;
import com.pleosoft.pleodox.data.TemplateOptions;

public interface TemplateGenerator {

	public void generate(InputStream templateStream, OutputStream os, PleodoxRequest dataroot, TemplateOptions options) throws Exception;
	public boolean isTransformable(String templateName, TemplateOptions options);
}
