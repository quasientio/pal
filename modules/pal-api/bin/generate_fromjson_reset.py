#!/usr/bin/env python
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


import os
import sys
import re
import javalang


all_classes = {}
required_imports = [
    'import com.google.gson.JsonArray;',
    'import com.google.gson.JsonObject;',
    'import com.google.gson.JsonParseException;'
]

PRIMITIVE_DEFAULTS = {
    'boolean': 'false',
    'byte': '(byte) 0',
    'short': '(short) 0',
    'char': "(char) 0",
    'int': '0',
    'long': '0L',
    'float': '0f',
    'double': '0d',
}

def extract_class_info(file_path):
    class_info = {}
    with open(file_path, 'r') as java_file:
        content = java_file.read()
        try:
            tree = javalang.parse.parse(content)
            for path, node in tree.filter(javalang.tree.ClassDeclaration):
                # skip static nested classes (we only generate for top-level/non-static)
                if 'static' in node.modifiers:
                    continue

                class_info['class'] = node.name
                class_info['methods'] = [m.name for m in node.methods]

                # detect zero-arg reset() on the top-level class
                has_zero_arg_reset = any(
                    (m.name == 'reset' and len(m.parameters) == 0) for m in node.methods
                )
                class_info['has_zero_arg_reset'] = has_zero_arg_reset

                fields = []
                for f in node.fields:
                    if 'static' in f.modifiers:
                        continue
                    decl = f.declarators[0]
                    field_name = decl.name
                    cap = field_name[0].upper() + field_name[1:]
                    field_getter = f'get{cap}'
                    field_setter = f'set{cap}'
                    is_array = len(getattr(f.type, 'dimensions', []) or []) > 0
                    field_type = f.type.name  # simple name
                    if field_getter in class_info['methods'] and field_setter in class_info['methods']:
                        fields.append({'name': field_name, 'type': field_type, 'is_array': is_array})
                class_info['fields'] = fields

        except javalang.parser.JavaSyntaxError as e:
            print(f"Syntax error in file {file_path}: {e}")
    return class_info


def insert_imports(content, required_imports):
    # Regular expression to find the last import statement or package declaration
    last_import_or_package_regex = r'(import [^\n]+;\n)|(package [^\n]+;\n)'

    # Find all matches for the regex in the content
    matches = list(re.finditer(last_import_or_package_regex, content))

    if not matches:
        # No package or import statements found (unlikely, but just in case)
        insertion_index = 0
    else:
        # Get the end index of the last match (import or package statement)
        insertion_index = matches[-1].end()

    # Check if the required imports already exist
    for imp in required_imports:
        if imp not in content:
            # Insert each missing import after the last import or package statement
            content = content[:insertion_index] + imp + '\n' + content[insertion_index:]
            insertion_index += len(imp) + 1  # Adjust the insertion index for the next import

    return content


# ---------- fromJson() generation ----------

def generate_fromjson_for_field(field):
    field_name, field_type, is_array = field['name'], field['type'], field['is_array']

    code = ""

    if is_array:
      if field_type == 'int':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          this.{field_name}[i] = jsonArray.get(i).getAsInt();\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type == 'byte':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          this.{field_name}[i] = jsonArray.get(i).getAsByte();\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type == 'boolean':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          this.{field_name}[i] = jsonArray.get(i).getAsBoolean();\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type == 'String':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          this.{field_name}[i] = jsonArray.get(i).getAsString();\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type in all_classes.keys():
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          JsonObject jsonObj = jsonArray.get(i).getAsJsonObject();\n'
          code += f'          this.{field_name}[i] = new {field_type}().fromJson(jsonObj);\n'
          code += f'        }}\n'
          code += f'      }}\n'
      else:
          print(f'PASSING on type = {field_type}')

    else: # non-array types
      if field_type == 'int':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        this.{field_name} = json.get("{field_name}").getAsInt();\n'
          code += '      }\n\n'
      elif field_type == 'byte':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        this.{field_name} = json.get("{field_name}").getAsByte();\n'
          code += '      }\n\n'
      elif field_type == 'long':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        this.{field_name} = json.get("{field_name}").getAsLong();\n'
          code += '      }\n\n'
      elif field_type == 'boolean':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        this.{field_name} = json.get("{field_name}").getAsBoolean();\n'
          code += '      }\n\n'
      elif field_type == 'String':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        this.{field_name} = json.get("{field_name}").getAsString();\n'
          code += '      }\n\n'
      elif field_type in all_classes.keys():
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonObject jsonObj = json.getAsJsonObject("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}().fromJson(jsonObj);\n'
          code += '      }\n\n'
      else:
          print(f'PASSING on type = {field_type}')

    return code
 

def generate_fromjson_code(class_info):
    class_name, fields = class_info['class'], class_info['fields']
    method_code = f'\n'
    method_code += f'  @Override\n'
    method_code += f'  public {class_name} fromJson(JsonObject json) throws JsonParseException {{\n'
    method_code += '    try {\n'
    for field in fields:
        method_code += generate_fromjson_for_field(field)
    method_code += '    } catch (Exception e) {\n'
    method_code += '      throw new JsonParseException("Error deserializing json object: " + e.getMessage(), e);\n'
    method_code += '    }\n'
    method_code += '    return this;\n'
    method_code += '  }\n'
    return method_code


def insert_method_at_end(content, method_code):
    insertion_index = content.rfind('}')
    return content[:insertion_index] + method_code + content[insertion_index:]

# ---------- reset() generation ----------


def generate_reset_for_field(field):
    name = field['name']
    ftype = field['type']
    is_array = field['is_array']

    # Arrays: rely on init() to restore zero-array sentinel (Colfer does this).
    if is_array:
        return ''

    # Primitives → zero
    if ftype in PRIMITIVE_DEFAULTS:
        return f'    this.{name} = {PRIMITIVE_DEFAULTS[ftype]};\n'

    # Nested message (another Colfer class in same package) → null
    if ftype in all_classes.keys():
        return f'    this.{name} = null;\n'

    # Strings/others: init() already restores them to generator’s zero state (usually "")
    return ''



def generate_reset_code(class_info):
    # If the top-level class already has a zero-arg reset(), skip generation
    if class_info.get('has_zero_arg_reset', False):
        return None

    fields = class_info['fields']
    body = ''.join(generate_reset_for_field(f) for f in fields)

    method_code  = '\n'
    method_code += '  /**\n'
    method_code += '   * Resets this Colfer message to its zero state for reuse on the hot path.\n'
    method_code += '   * Calls init(), then zeros primitives and nulls nested messages.\n'
    method_code += '   */\n'
    method_code += '  public void reset() {\n'
    method_code += '    init();\n'
    if body:
        method_code += body
    method_code += '  }\n'
    return method_code

# ---------- main ----------

# Base path to the Java classes
pal_home = os.environ.get('PAL_HOME')
if not pal_home:
  print("PAL_HOME is not defined. Aborting.")
  sys.exit(1)
  
java_classes_base_path = f'{pal_home}/modules/pal-api/src/main/java/io/quasient/pal/messages/colfer'

# First pass: parse all classes to populate all_classes (for nested message detection)
for filename in os.listdir(java_classes_base_path):
    if filename == "package-info.java":
        continue
    java_file_path = os.path.join(java_classes_base_path, filename)
    class_info = extract_class_info(java_file_path)
    if not class_info:
        continue
    class_info['file_path'] = java_file_path
    class_name = class_info['class']
    all_classes[class_name] = class_info

# Second pass: add imports, fromJson, and reset()
for class_info in all_classes.values():
    java_file_path = class_info['file_path']
    with open(java_file_path, 'r') as file:
        content = file.read()

    # ensure imports (for fromJson only)
    content = insert_imports(content, required_imports)

    # generate & insert fromJson (only if missing; cheap string check is OK here)
    if ' fromJson(JsonObject json)' not in content:
        fromjson_method_code = generate_fromjson_code(class_info)
        content = insert_method_at_end(content, fromjson_method_code)

    # generate & insert reset() using AST-derived flag
    reset_method_code = generate_reset_code(class_info)
    if reset_method_code:
        content = insert_method_at_end(content, reset_method_code)

    with open(java_file_path, 'w') as file:
        file.write(content)

print("Done: fromJson() and reset() injected where missing.")
