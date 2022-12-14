#
# Copyright 2022 Exactpro (Exactpro Systems Limited)
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
import shutil
import os
import sys
import json
import re
import subprocess
import tarfile
import threading
from typing import List

CONFIG_FILE_NAME: str = "extract_config.json"
SCRIPT_FILE_NAME: str = "docker_extractor.sh"

RUN_SCRIPT_POSTFIX: str = os.sep + "bin" + os.sep + "service"
SERVICE_DIR_POSTFIX: str = os.sep + "home" + os.sep + "service"
HOME_DIR_POSTFIX: str = os.sep + "home" + os.sep
COMPONENTS_DIR: str = "components"
HOME_IGNORE: List[str] = ["service", "Dockerfile"]
SERVICE_IGNORE: List[str] = ["lib"]
MAIN_CLASS_LINE_INDICATOR: str = "eval set --"
CLASSPATH_INDICTOR: str = "CLASSPATH=$APP_HOME/lib/"
LIB_DIR: str = "lib"
BIN_DIR: str = "bin"

def substring_after(s, delim):
    return s.partition(delim)[2]

def substring_before(s, delim):
    return s.partition(delim)[0]

class Config(object):
    def __init__(self, images, layers_config, registry_mapping):
        self.images = images
        self.layers_config = layers_config
        self.registry_mapping = registry_mapping

class ImageExtractor(object):
    def __init__(self, image_name: str, cwd: str, config: Config):
        self.image_name = image_name
        self.cwd = cwd
        self.config = config
        self.components_dir = os.path.join(self.cwd, COMPONENTS_DIR)

    def extract_and_prepare(self):
        lib_dir = os.path.join(self.components_dir, LIB_DIR)
        self.__prepare_directory(self.__call_script(), lib_dir)


    def __call_script(self) -> str:
        number_of_layers = 1
        for (key, value) in self.config.layers_config.items():
            if(self.image_name.__contains__(key)):
                number_of_layers = value

        domain = self.image_name.split("/")[0]
        is_mapping_presented = domain in self.config.registry_mapping

        auth_type = "token"
        if(is_mapping_presented):
            auth_type = "basic"
            [user, password] = self.config.registry_mapping[domain].split(":")
            os.environ["BASIC_USER"] = user
            os.environ["BASIC_PASSWORD"] = password
        out_dir = os.path.join(self.components_dir, self.image_name.split("/")[-1])
        args_list = [
            '-n', str(number_of_layers),
            '-o', out_dir,
            '-t', auth_type,
            self.image_name
        ]
        execution_result = subprocess.run([os.path.join(self.cwd, SCRIPT_FILE_NAME),
                *args_list])
        if(execution_result.returncode != 0 or not os.path.exists(out_dir)):
            raise RuntimeException("Error while loading image file for {0}".format(out_dir) )
        return out_dir

    def __prepare_directory(self, dir, lib_dir):
        cwd = dir
        home_dir = cwd + HOME_DIR_POSTFIX
        if not os.path.exists(home_dir):
            shutil.rmtree(cwd)
            return
        for filename in os.listdir(cwd):
            if filename == LIB_DIR:
                shutil.rmtree(os.path.join(cwd, LIB_DIR))
            if filename == BIN_DIR:
                shutil.rmtree(os.path.join(cwd, BIN_DIR))

        for filename in os.listdir(home_dir):
            if(filename in HOME_IGNORE):
                continue
            shutil.move(home_dir + os.sep + filename, cwd)

        service_dir = cwd + SERVICE_DIR_POSTFIX
        service_lib_dir = os.path.join(service_dir, LIB_DIR)
        service_main_lib = self.__extract_main_lib(service_dir + RUN_SCRIPT_POSTFIX)
        libs = []
        if os.path.exists(service_lib_dir) and os.path.isdir(service_lib_dir):
            for filename in os.listdir(service_lib_dir):
                if filename == service_main_lib:
                    continue
                libs.append(filename)
                if not os.path.exists(os.path.join(lib_dir, filename)):
                    shutil.move(os.path.join(service_lib_dir, filename), lib_dir)
                else:
                    os.remove(os.path.join(service_lib_dir, filename))

        self.__dump_libs_config({"libs": libs}, cwd)

        for filename in os.listdir(service_dir):
            shutil.move(service_dir + os.sep + filename, cwd)

        shutil.rmtree(cwd + HOME_DIR_POSTFIX)
        self.__extract_main_class(cwd + RUN_SCRIPT_POSTFIX, cwd)

    def __dump_libs_config(self, libs_config, cwd):
         with open(os.path.join(cwd, "libConfig.json"), "w") as lib_file:
             lib_file.write(json.dumps(libs_config))


    def __extract_main_lib(self, run_file):
        with open(run_file) as file:
            for line in file:
                if line.__contains__(CLASSPATH_INDICTOR):
                    return substring_before(substring_after(line.rstrip(), CLASSPATH_INDICTOR), ":")
        raise RuntimeError("Not found main lib")


    def __extract_main_class(self, run_file, cwd):
        with open(run_file) as file:
            with open(cwd + os.sep + "mainclass", "a") as result:
                for line in file:
                    if(line.__contains__(MAIN_CLASS_LINE_INDICATOR)):
                        result.write(line.rstrip().split(" ")[-2])




if __name__ == "__main__":
    config_file = open(CONFIG_FILE_NAME)
    j = json.load(config_file)
    config_file.close()

    config = Config(**j)
    cwd = os.getcwd()

    components_dir = os.path.join(cwd, COMPONENTS_DIR)
    lib_dir = os.path.join(components_dir, LIB_DIR)
    os.makedirs(lib_dir)

    threads: List[threading.Thread] = []
    for image in config.images:
        extractor = ImageExtractor(image, cwd, config)
        threads.append(threading.Thread(target=extractor.extract_and_prepare))

    for x in threads:
        x.start()

    for x in threads:
        x.join()
