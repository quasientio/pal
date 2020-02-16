<p align="center">
![pal logo](site/imgs/PAL-logo-01.png)
<br>
    <h1 align="center">The friendly Java runtime with superpowers</h1>
</p>


### Introduction and Usage

PAL is a runtime system which uses message-passing to automatically expand the underlying program’s execution model, making 
messages the unit of work for all desired classes, which will usually be your domain or business objects.
 
Less concisely and more precisely, PAL is a message-oriented middleware which, acting as a message-passing runtime system, transparently 
and automatically transforms interaction within object-oriented programs, converting direct object invocation into communication via message-passing.
During execution, it forwards and receives messages through synchronous and asynchronous channels. PAL does not change the program's semantics.
Because it is transparent and implicit, it makes java applications run as if inter- and intra-object messaging was inherent to the language, implemented by the JVM itself.

As Alan Kay, father of object-oriented programming, [once said](https://en.wikipedia.org/wiki/Alan_Kay#cite_note-8)
>  I'm sorry that I long ago coined the term "objects" for this topic because it gets many people to focus on the lesser idea. The big idea is "messaging"

For usage information, and a full introduction to PAL, head to the [online documentation](https://docs.palrt.org).

### Contributing

Ideas and bug reports are welcome, preferably in the form of [issues](https://gitlab.com/cometera/pal/issues).

Code contributions are more than welcome. Code is contributed as usual, via [merge requests](https://gitlab.com/cometera/pal/-/merge_requests).

Contributors are expected to adhere to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

### Development

Pal is 100% java, laid out as a multi-module Maven project.

#### Required dependencies:
* JDK 1.8
* Maven 3
* Git
* Docker (recommended)

#### Optional dependencies
* If you wish to run zookeeper natively (i.e. not inside docker), install [zookeeper](https://zookeeper.apache.org/releases.html#download)
* If you wish to run kafka natively (i.e. not inside docker), install [kafka](https://kafka.apache.org/downloads)
* To recompile protobuf source files, you will need to install [protocol buffers](https://developers.google.com/protocol-buffers/docs/downloads)

#### Build

Quickly build and install, running unittests: `mvn install -DskipITs`

### License

Pal is made available as open source under the terms of the [GNU General Public License v3.0](https://opensource.org/licenses/GPL-3.0).

