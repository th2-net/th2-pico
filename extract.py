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
import subprocess
import tarfile
import threading
from typing import List

CONFIG_FILE_NAME: str = "images_to_load.json"
SCRIPT_FILE_NAME: str = "docker_extractor.sh"
RUN_SCRIPT_POSTFIX: str = os.sep + "bin" + os.sep + "service"
SERVICE_DIR_POSTFIX: str = os.sep + "home" + os.sep + "service"
HOME_DIR_POSTFIX: str = os.sep + "home" + os.sep
LIB_DIR: str = os.sep + "components" + os.sep
HOME_IGNORE: List[str] = ["service", "Dockerfile"]
MAIN_CLASS_LINE_INDICATOR: str = "eval set --"
EXTRA_LAYERS_IMAGES: List[str] = [
    'codec-fix',
    'codec-ntg',
    'codec-fast',
    'codec-itch',
    'codec-soup',
    'conn-fix',
    'conn-ntg',
    'conn-soup'
]

def make_tarfile(output_filename, source_dir):
    with tarfile.open(output_filename, "w:gz") as tar:
        for file in os.listdir(source_dir):
            p = os.path.join(source_dir, file)
            tar.add(p, arcname=file)

class Config(object):
    def __init__(self, images):
        self.images = images

class ImageExtractor(object):
    def __init__(self, image_name: str, cwd: str):
        self.image_name = image_name
        self.cwd = cwd

    def extract_and_prepare(self):
        out_dir = self.__call_script()
        self.__prepare_directory(out_dir)


    def __call_script(self) -> str:
        number_of_layers = 1
        for image_part in EXTRA_LAYERS_IMAGES:
            if(self.image_name.__contains__(image_part)):
                number_of_layers = 4
        out_dir = self.cwd + LIB_DIR + os.sep + self.image_name.split("/")[-1]
        domain = "ghcr"
        if(self.image_name.__contains__("nexus")):
            domain = "nexus"
        args_list = [
            '-n', str(number_of_layers), 
            '-o', out_dir,
            '-t', domain,
            self.image_name
        ]
        print("Started extraction of image {0}".format(self.image_name))
        execution_result = subprocess.run([self.cwd + os.sep + SCRIPT_FILE_NAME,
                *args_list])
        print()
        if(execution_result.returncode != 0 or not os.path.exists(out_dir)):
            sys.exit(1)
        return out_dir
    
    def __prepare_directory(self, dir):
        cwd = dir
        home_dir = cwd + HOME_DIR_POSTFIX
        for filename in os.listdir(home_dir):
            if(filename in HOME_IGNORE):
                continue
            shutil.move(home_dir + os.sep + filename, cwd)
        
        service_dir = cwd + SERVICE_DIR_POSTFIX
        for filename in os.listdir(service_dir):
            shutil.move(service_dir + os.sep + filename, cwd)
        shutil.rmtree(cwd + HOME_DIR_POSTFIX)
        self.__extract_main_class(cwd + RUN_SCRIPT_POSTFIX, cwd)

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
    threads: List[threading.Thread] = []
    for image in config.images:
        extractor = ImageExtractor(image, cwd)
        threads.append(threading.Thread(target=extractor.extract_and_prepare))

    for x in threads:
        x.start()

    for x in threads:
        x.join()

    #make_tarfile("components.tar", cwd + LIB_DIR)
    #shutil.rmtree(cwd + LIB_DIR)
