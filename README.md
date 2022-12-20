# Pico
Tool for bootstrapping th2 components locally.

## Usage
```
 -b,--bootstrap-type <arg>   Type of bootstrapping to use for the bundle.
                             Possible values: shell, classloader. Default:
                             classloader
 -h,--help                   Displays this help message.
 -m,--mode <arg>             Operator mode to run. Possible values: full,
                             configs. Default: full
 -o,--components <arg>       Absolute or relative path to the directory
                             containing component files. Default: bin/components
```

- bootstrap type - It decides how the components will be loaded. Either by multiple classloaders (`classloader`) or by shell scripts (`shell`). `classloader` will not restart components if one of them exited.
- mode
  - full: pico-operator will generate config files for the components and will generate rabbitMq queues and links.
  - configs: only configs will be generated
- components dir - path to dir where component libs and starter script is located

## Schema repo preparation
If version of infra of schema repo is bellow v2.0 you can use [Converter](https://github.com/th2-net/th2-cr-converter)
1. clone converter repo
2. build code using `gradle build` command
3. run jar with `"-Dconverter.config=./path/to/converter-config"`

converter-config example:
```yaml
git:
  localRepositoryRoot: ..\schemas -- link to schema repo directory
```
4. pass appropriate arguments:
   * `local` for running locally 
   * `my-schema` name of the directory containing th2 schema
   * `v2` version to with to convert

example of local run command:
`java "-Dconverter.config=./converter-config.yml"  -jar .\build\libs\th2-cr-converter-1.1.2.jar local th2-infra-schema-demo v2`

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
This will create rabbitMq container with management plugin and default vhost `th2`, management port 15672 and default application port 5672 and guest/guest credentials.

To see other configuration options checkout [RabbitMq docker image](https://hub.docker.com/_/rabbitmq)

Another option is to install rabbitMq locally. But this option is not tested. [rabbitMq installation guide](https://www.rabbitmq.com/download.html)

### Bundle creation
1. fill `images` list in extract_config with components you want to use in bundle
2. push changes to new branch
3. after build is finished, download bundle from artifacts ([artifacts](https://gitlab.exactpro.com/vivarium/th2/th2-core-proprietary/th2-pico/-/pipelines))

### prepare to run pico-operator
1. put directory with box descriptions from [Prepared directory](#Schema repo preparation) into bin directory of bundle
2. provide default configs for each box to be copied. bundle them in a separate directory. place logging files under `loggin` folder in this directory.
   Add path to location of default configs in pico operator config file.
3. create config for operator:
```yaml
schemaName: schema # name of schema
repoLocation: path/to/repository/to/generate/configs/from
defaultSchemaConfigs:
  location: /path/to/default/schema/configs
  configNames:
    cradle: cradle.json
    cradleManager: cradle_manager.json
    bookConfig: book_config.json
    grpcRouter: grpc_router.json
    mqRouter: mq_router.json
    rabbitMQ: rabbitMQ.json
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
4. you can either put config into bin folder or specify path to it with -Dpico.operator.config path/to/config.yaml during bundle startup

### Run bundle
2. run bin/pico or bin/pico -b classloader

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

