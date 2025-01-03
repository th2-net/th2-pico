# Pico (0.0.13)
Tool for bootstrapping th2 components locally.

## Demo
This schema is example of working pico bundle which is based on [link](https://github.com/th2-net/th2-infra-schema-demo/tree/ver-1.6.1-main_scenario)
1. sim and reads are already in bundle (not external boxes as described in link)
2. you need to run recon and ui by yourself. (Not supported features)[#Not supported features]


## Run demo
1. Download artifact from [artifacts](https://gitlab.exactpro.com/vivarium/th2/th2-core-proprietary/th2-pico/-/pipelines)
2. Unzip the artifact
3. Create "work" directory near bin and cfg directories
4. Download bundle images from [bundle_images](https://gitlab.exactpro.com/vivarium/th2/th2-core-proprietary/th2-pico-bundles/-/pipelines)
5. unzip images and put component directory near "work" "bin" and "cfg" directories (bin  cfg  components  configs  lib  logs  th2-infra-schema-demo-converted  work)
6. Download and prepare a schema (described below). Put converted schema to th2-infra-schema-demo-converted directory
7. Setup cassandra and rabbit as described in [rabbit](#RabbitMq setup), [cassandra](#Cassandra setup)
8. Go to bin directory and run ./pico -m full -w ../work -o ../components (will generate configs for all components and will setup queues in rabbitmq)
9. Stop pico by Ctrl+C
10. This is temporary step. Pico will change these configs in the future by itself. Change all `workspace` property in all configs to `absolute/path/to/pico/work/name_of_box/`
11. Run pico without regenerating the config run ./pico -w ../work -o ../components



## Usage
```
 -tdc,--th2-config                      Absolute or relative path to th2 configurations settings.
 -pc,--pico-config                      Absolute or relative path to pico config.
 -b,--bootstrap-type <arg>              Type of bootstrapping to use for the bundle.
                                        Possible values: shell, classloader. Default:
                                        classloader
 -h,--help                              Displays this help message.
 -m,--mode <arg>                        Operator mode to run. Possible values: full,
                                        configs, none. Default: none
 -w,--workDir                           Absolute or relative path to the work directory 
                                        where pico manages all boxes instances.
 -s,--stateFolder <arg>                 Absolute or relative path to the directory 
                                        for storing JSONs with state information
 -o,--components <arg>                  Absolute or relative path to the directory
                                        containing component files. Default: bin/components
```

- bootstrap type - It decides how the components will be loaded. Either by multiple classloaders (`classloader`) or by shell scripts (`shell`). `classloader` will not restart components if one of them exited.
- mode
    - full: pico-operator will generate config files for the components and will generate rabbitMq queues and links.
    - configs: only configs will be generated
    - none: pico operator will not be used
- components dir - path to dir where component libs and starter script is located

## Schema repo preparation
If version of infra of schema repo is bellow v2.0 you can use [Converter](https://github.com/th2-net/th2-cr-converter)
The converter binaries already included to pico package. In order to use it:
1. clone or copy a schema inside demo_schema directory near work bin and etc. For instance ls demo_schema outputs th2-infra-schema-demo, where th2-infra-schema-demo is output of git clone https://github.com/th2-net/th2-infra-schema-demo.git
2. run ./bin/schema-converter local th2-infra-schema-demo v2
3. if no problems directory demo_schema has to contain converter directory th2-infra-schema-demo-converted

schema-converter script uses cfg/schema_converter.cfg config file:
```yaml
git:
  localRepositoryRoot: ..\demo_schema -- link to schema repo directory
```
Arguments which was passed to the script:
* `local` for running locally
* `my-schema` name of the directory containing th2 schema inside directory specified in the config file
* `v2` version to with to convert


This will generate new schema directory compatible with infra v2.0 and will be later required to run bundle.

Generated schema will be placed at the same level as the original schema, with name `ORIGINAL_SCHEMA_NAME-converted`

## Setup and run
### Cassandra setup
- Download cassandra from [here](https://cassandra.apache.org/_/download.html)
- Unzip it where you want.
- configure cassandra.yaml whatever you want
- Run `bin/cassandra` depending on your OS.
- It will be started with default user/password `cassandra/cassandra` you can then change it to wanted one.

### RabbitMq setup
1. run `docker run -d --hostname th2 --name some-rabbit -e RABBITMQ_DEFAULT_VHOST=th2 -p 15672:15672 -p 5672:5672 rabbitmq:3-management`
   script for running this is located at bin directory
   This will create rabbitMq container with management plugin and default vhost `th2`, management port 15672 and default application port 5672 and guest/guest credentials.

To see other configuration options checkout [RabbitMq docker image](https://hub.docker.com/_/rabbitmq)

Another option is to install rabbitMq locally. But this option is not tested. [rabbitMq installation guide](https://www.rabbitmq.com/download.html)

### Bundle creation
Take one from [here](https://gitlab.exactpro.com/vivarium/th2/th2-core-proprietary/th2-pico-bundles/-/pipelines) or create your own using instruction from [here](https://gitlab.exactpro.com/vivarium/th2/th2-core-proprietary/th2-pico-bundles/-/tree/demo)

### prepare to run pico-operator
1. put directory with box descriptions from [Prepared directory](#Schema repo preparation) into bin directory of bundle
2. provide default configs for each box to be copied. bundle them in a separate directory. place logging files under `loggin` folder in this directory.
   Add path to location of default configs in pico operator config file.
3. create `th2-default-config.yml` for operator:
```yaml
schemaName: schema # name of schema
repoLocation: path/to/repository/to/generate/configs/from
defaultSchemaConfigs:
  location: /path/to/default/schema/configs
  configNames:
    cradle: cradle.json
    cradleManager: cradle_manager.json
    grpcRouter: grpc_router.json
    mqRouter: mq_router.json
    rabbitMQ: rabbitMQ.json
    log4j2Config: log4j2.properties
    log4pyConfig: log4py.conf
    zeroLogConfig: zerolog.properties
rabbitMQManagement:
  host: localhost
  managementPort: 15672
  applicationPort: 5672
  vhostName: th2
  exchangeName: global-notification
  username: guest
  password: guest
  persistence: true
  schemaPermissions:
    configure: ""
    read: ".*"
    write: ".*"
grpc: # pull of grpc ports to be used by components
  serverPorts:
    start: 8091
    end: 8189
```

### optional

create `pico-config.yml` config for pico and pass using `-pc/--pico-config` arguments
```yaml
componentConfig:
  # auto-scaling functionality works when pico detect component crash by out of memory reason
  #  java: jvm generate `java_pid<pid>.hprof` file after crash component.
  #        default environment variables should include values:
  #          JAVA_TOOL_OPTIONS: ["-XX:+ExitOnOutOfMemoryError", "-XX:+HeapDumpOnOutOfMemoryError"]
  memoryAutoScalingConfig:
    maxMemory: 2000 # upper limit for auto-scaling in megabytes 
    growthFactor: 1.5 #  new values size is calculated by the formula `previous memory * growthFactor`
  defaultEnvironmentVariables: # these environment variable are applied for each component controlled by pico 
    JAVA_TOOL_OPTIONS:
    - "-XX:+ExitOnOutOfMemoryError"
    - "-XX:+HeapDumpOnOutOfMemoryError"
    - "-Dlog4j2.shutdownHookEnabled=false"
  beforeRestartTimeout: 5000 # timeout in milliseconds between component crash and the next start 
```

### Run bundle

`run bin/pico or bin/pico --bootstrap-type classloader --th2-default-config th2-default-config.yml`

### Caveats
generated `custom.json` configs can contain wrong `host/ports` or `paths` configuration to run bundle locally.
You need to fix them manually.
You need to check:
1. all ports are unique
2. all paths are correct within current system where bundle is running
3. all hosts must be `localhost`

# Not supported features
- python components
- ui components

## Workarounds
### Python
For python components you can clone component repository you wish to run, activate venv and run it manually.
### UI
There is no universal way to run UI components locally.
#### report-ui
1. clone report-ui repo
2. run `npm install`
3. change webpack/webpack.dev.js to:
```js
    const webpackMerge = require('webpack-merge');
    const commonConfig = require('./webpack.common');
    const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');
    const { appSrc } = require('./paths');
    
    module.exports = webpackMerge(commonConfig, {
        output: {
            publicPath: '/',
        },
        mode: 'development',
        entry: ['react-hot-loader/patch', appSrc],
        devtool: 'inline-source-map',
        devServer: {
            watchFiles: {
                options: {
                    usePolling: true,
                    ignored: ['src/__tests__/', '**/node_modules'],
                },
            },
            client: {
                overlay: false,
            },
            compress: true,
            port: **ui-port**,
            host: '0.0.0.0',
            historyApiFallback: true,
            proxy: {
                '/backend': {
                    target: '**link-to-rpt-data-provider**',
                    pathRewrite: {'^/backend': ''},
                },
            },
            hot: true,
        },
        module: {
            rules: [
                {
                    test: /\.scss$/,
                    exclude: /node_modules/,
                    use: ['style-loader', 'css-loader', 'sass-loader'].filter(loader => loader),
                },
            ],
        },
        plugins: [
            new ForkTsCheckerWebpackPlugin({
                eslint: {
                    files: './src/**/*',
                },
            }),
        ],
    });
```
where:
- **link-to-rpt-data-provider** - link to rpt-data-provider (can be found in `custom.json` config for rpt-data-provider)
- **ui-port** - port you want to run ui on
4. run `npm run start`

# Shutdown
If you used `ctrl+c` to stop bundle, it will shut down properly with closing all resources.
In case you killed pico with -9 option you need to use `shutdown.sh` script to close all resources properly.

`shutdown.sh` has several execution modes:
+ without arguments: stop all pico subprocess property
  Script looks for actual PIDs by '.../pico/workspace/configs' pattern
+ -p/--process <PID>: stop process by PID and its child processes
+ -c/--component <component name>: stop pico component by schema name and its child processes
  Script looks for actual PID in <starus folder>/<component name>.json file

# Release notes

## 0.0.13
+ Migrated to th2 gradle plugin `0.1.6`

## 0.0.12
+ Replaced Console to Rolling file appender in default log4j2.properties
    + Components write log to `logs/app.log` file
    + Pico writes system output/error to `logs/system.log` file

## 0.0.11
### Fix:
+ Fixed bug: OutOfMemory due to keeping all component's output in memory

### Update:
+ Migrated to th2 gradle plugin `0.1.1`
+ pico-operator `1.7.0-dev`

## 0.0.10
+ Migrated to th2 gradle plugin `0.0.8`
+ Migrated to th2 bom: `4.6.1
+ Updated:
  + commons-cli: `1.6.0`
  + commons-io: `2.15.1`
  + kotlin-logging: `3.0.5`
  + slf4j = `2.0.7`
    log4j = `2.23.0`
  + commons-text: `1.11.0`

## 0.0.9
+ Fixed the pico copies default `cradle_manager.json` config instead of generated problem
+ Updated pico-operator:1.5.2-dev

## 0.0.8
+ Added `-pc,--pico-config` and `-tdc,--th2-default-config` arguments for pico run.
+ Updated pico-operator:1.5.1-dev

## 0.0.7

### Fix:
+ `log4j2.properties` for pico application corrected to avoid random file deletion

## 0.0.6
### Fix:
+ `shutdown.sh` script can't kill components run sub-processes gracefully
### Feature:
+ added kill process by PID and component by name modes into `shutdown.sh`

## 0.0.5
### Feature:
+ Pico captures sysout / syserr of component process

### Fix:
+ Pico doesn't close some components sometimes

## 0.0.4
+ Use book name from infra manager config or from component custom resource

## 0.0.3
+ added logic for destroying descendants process for run component

## 0.0.2

### Feature:
+ added log4j2Config, log4pyConfig, zeroLogConfig config names.

### Fix:
+ the `Started component ...` info log prints too long line.
+ conditionally using `cradle manager` and `grpc router` configs from component custom resource.

### Update:
+ pico-operator `1.4.0-dev`