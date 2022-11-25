# Pico
Tool for bootstrapping th2 components in a single process.

## Pico configuration
```yaml
componentsDir: path/to/components/dir
configsDir: path/to/configs/dir
```

### Components Dir
Expected layout is:
components
    | -- component1
        | -- lib ( jars )
        | -- bin ( bootstrap scripts )
        | -- mainclass (file containing mainclass for current component)
    | -- component2
        | -- lib ( jars )
        | -- bin ( bootstrap scripts )
        | -- mainclass (file containing mainclass for current component)
...

### Configs dir
configs
    | -- component1
        | -- box.json ( required to get which image to use)
        | -- other config files
    | -- component2
        | -- box.json ( required to get which image to use)
        | -- other config files

# Components extraction
You should list images to download in `images_to_load.json` file. This will download images content required to bootstrap application and make directory structure for components which is described above. 