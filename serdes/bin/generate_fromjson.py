#!/usr/bin/env python

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


def extract_class_info(file_path):
    class_info = {}
    with open(file_path, 'r') as java_file:
        content = java_file.read()
        try:
            tree = javalang.parse.parse(content)
            for path, node in tree.filter(javalang.tree.ClassDeclaration):
                # not interested in static classes
                if 'static' in node.modifiers:
                  continue
                class_info['class'] = node.name

                # parse methods, we only care about their name
                methods = []
                for method in node.methods:
                    methods.append(method.name)

                """ parse fields: we will only extract those with a getter and setter
                 since we assume those and only those correspond to the fields in 
                 our colfer messages """
                fields = []
                for f in node.fields:
                  field_name = f.declarators[0].name
                  field_getter = f'get{field_name[0].upper() + field_name[1:]}' 
                  field_setter = f'set{field_name[0].upper() + field_name[1:]}' 
                  is_array = len(f.type.dimensions) > 0
                  if field_getter in methods and field_setter in methods:
                    fields.append({'name': field_name, 'type': f.type.name, 'is_array': is_array})
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


def generate_fromjson_for_field(field):
    field_name, field_type, is_array = field['name'], field['type'], field['is_array']

    code = ""

    if is_array:
      if field_type == 'int':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          int jsonInt = jsonArray.get(i).getAsJsonInt();\n'
          code += f'          this.{field_name}[i] = new {field_type}().fromJson(jsonInt);\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type == 'byte':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          byte jsonByte = jsonArray.get(i).getAsByte();\n'
          code += f'          this.{field_name}[i] = new {field_type}().fromJson(jsonByte);\n'
          code += f'        }}\n'
          code += f'      }}\n'
      elif field_type == 'boolean':
          code += f'      if (json.has("{field_name}")) {{\n'
          code += f'        JsonArray jsonArray = json.getAsJsonArray("{field_name}");\n'
          code += f'        this.{field_name} = new {field_type}[jsonArray.size()];\n'
          code += f'        for (int i = 0; i < jsonArray.size(); i++) {{\n'
          code += f'          boolean jsonBool = jsonArray.get(i).getAsJsonBoolean();\n'
          code += f'          this.{field_name}[i] = new {field_type}().fromJson(jsonBool);\n'
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


def insert_fromjson_method(content, fromjson_code):
    insertion_index = content.rfind('}')
    return content[:insertion_index] + fromjson_code + content[insertion_index:]


# Base path to the Java classes
pal_home = os.environ.get('PAL_HOME')
if not pal_home:
  print("PAL_HOME is not defined. Aborting.")
  sys.exit(1)
  
java_classes_base_path = f'{pal_home}/serdes/src/main/java/net/ittera/pal/messages/colfer'
# parse all classes to extract class and fields info
for filename in os.listdir(java_classes_base_path):
    java_file_path = os.path.join(java_classes_base_path, filename)
    class_info = extract_class_info(java_file_path)
    class_info['file_path'] = java_file_path
    class_name = class_info['class']
    all_classes[class_name] = class_info


"""
Go through all classes, generating and inserting:
- the imports
- the fromJson() method code

Note that we cannot do both the above and this in one pass, because generate_fromjson_code()
needs all_classes to be already loaded
"""
for class_info in all_classes.values():
    # generate fromJson
    fromjson_method_code = generate_fromjson_code(class_info)
    java_file_path = class_info['file_path']

    with open(java_file_path, 'r') as file:
        content = file.read()
        content = insert_imports(content, required_imports)
        content = insert_fromjson_method(content, fromjson_method_code)
    with open(java_file_path, 'w') as file:
        file.write(content)
