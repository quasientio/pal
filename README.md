<p align="center">
![pal logo](site/imgs/PAL-logo-01.png)
<br>
    <h1 align="center">PAL, a friendly runtime for a connected age</h1>
</p>


## Introduction

PAL is a runtime system which uses message-passing to automatically expand the underlying program’s execution model, making 
messages the unit of work for all desired classes, which will usually be your domain or business objects.
 
Less concisely and more precisely, PAL is a message-oriented middleware which, acting as a message-passing runtime system, transparently 
and automatically transforms interaction within object-oriented programs, converting direct object invocation into communication via message-passing.
During execution, it forwards and receives messages through synchronous and asynchronous channels. PAL does not change the program's semantics.
Because it is transparent and implicit, it makes java applications run as if inter- and intra-object messaging was inherent to the language, implemented by the JVM itself.

As Alan Kay, father of object-oriented programming, [once said](https://en.wikipedia.org/wiki/Alan_Kay#cite_note-8)
>  I'm sorry that I long ago coined the term "objects" for this topic because it gets many people to focus on the lesser idea. The big idea is "messaging"

PAL is an embodiment of this realization, an exploration into _the big idea_. PAL implements this concept in Java,
empowering programs with message-passing capabilities, by translating control (via direct invocations)
into communication (via messages), and enabling a set of standard external interfaces (RPC, REST, PUB/SUB, etc)
out of the box.

### What PAL isn't
* It's not a new programming language.
* It's not a new programming model. PAL intends to make object-oriented code run as it was meant to :)
* It's not an actor model implementation, although it shares with Akka (or Erlang) many benefits of the loose 
  coupling derived from a messaging approach. As opposed to Akka, PAL does not require the programmer to step outside
 the object-oriented paradigm. Also, in PAL most messaging is designed to work transparently, not explicitly.
 
### So what is PAL?
PAL is best understood as an extension or fix to the java runtime. Therefore, you can adopt PAL as a simple drop-in
replacement to the JVM, whatever is the domain, purpose and scale of your java application or service.
PAL offers a set of out-of-the-box features that you can turn on and leverage as you may see fit.

#### Other ways to describe PAL
 * PAL is a micro container for java services and applications, allowing easy distribution, concurrency and integration
  in a dynamic, transparent, extensible and manageable runtime layer.
 * PAL acts as a thin runtime layer on top of the JVM that makes outrageously easy to connect, monitor, control, deploy, 
  audit, dynamically modify, intercept, scale, replay and distribute your java objects, services and applications.
 * PAL is an all-purpose middleware layer for JVM-based languages, acting as a networked AOP platform.

In 5 words, PAL is all about: connectivity, flexibility, extensibility, visibility, and management.

As such, PAL is the ideal platform on which to design systems and compose functionality. PAL's set of out-of-the-box
networking features and opinionated stand on messages as first-class entities, empowers horizontal scaling and 
enables streamlining the design of complex systems, allowing an easier venture into worlds outside the client-server
architectural universe.

### What's in the name?
Most mainstream applications and frameworks offer strict, explicitly defined and limited interfaces
and protocols through which a rigid interaction with remote clients/servers/peers may happen. 

The name PAL makes reference to the inherent __friendliness__ that stems from a system that is naturally open to
communication, and that provides a set of communication methods and channels that enable a rich,
dynamic and deep interaction.

As an added bonus, PAL is also an acronym of "Peers And Logs", the two primary entities in PAL-based systems.

## Getting started
Pal is 100% java, laid out as a multi-module Maven project.

### Build
#### Required dependencies:
* JDK 1.8
* Maven 3
* Git
* Docker (recommended)

Quickly build and install, running all unittests: `mvn install -DskipITs`

The above command skips integration tests. To run these as well, follow the instructions in the [itt README](itt/README.md).

### Download
As an alternative to building PAL from its sources, you may get the binaries of the latest release.
Browse to the [PAL Package Registry](https://gitlab.com/cometera/pal/-/packages) and click on
the __net/ittera/pal/pal_installer__ package. Under Assets click on the pal_installer-VERSION-DATE.TAG-bin.zip file
or the similar .tar.gz file.
Once downloaded, simply unzip/tar-gunzip the file wherever you want to install it in your system.

### Usage
At the very minimum, you will need the JRE 8 (although we recommend installing the JDK).

In order to take full advantage of PAL, install Docker as well, so that you may use the provided images to run __etcd__
and __kafka__. Alternatively,
 * If you wish to run etcd natively (i.e. not inside docker), install [etcd](https://etcd.io/docs/v3.2/install/)
 * If you wish to run kafka natively (i.e. not inside docker), install [kafka](https://kafka.apache.org/downloads)

#### Define PAL_HOME and add the binaries to your PATH
Since the pal command is conceived as a drop-in replacement for the java command, we require similar additions to our
environment variables as we do for java.
In short,
* Export an env variable PAL_HOME pointing to the directory of installation.
* Add the $PAL_HOME/bin folder to your $PATH environment variable.

You should now be able to run the pal command from anywhere in your system.
Type `pal help` to print the Usage information.
```bash
libre@sparch:~ $ pal help
Usage: pal [OPTIONS] COMMAND

The friendly java runtime

Options:
  -d, --dir HOST:PORT   PAL directory
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.

Commands:
  run    Run a new peer
  print  Print messages from peers or logs
  call   Send messages to peers or logs
  ls     List peers and logs in directory
  rm     Remove peers or logs from directory
  help   Displays help information about the specified command

Run 'pal COMMAND --help' or 'pal help COMMAND' for more information on a command.
```

Next, check out the [HelloWorld](https://gitlab.com/cometera/pal-examples/-/tree/master/helloworld) and the [Spring
PetClinic](https://gitlab.com/cometera/pal-examples/-/tree/master/spring-petclinic) example apps.

## Contributing

Ideas and bug reports are welcome, preferably in the form of [issues](https://gitlab.com/cometera/pal/issues).

Code contributions are more than welcome. Code is contributed as usual, via [merge requests](https://gitlab.com/cometera/pal/-/merge_requests).

Contributors are expected to adhere to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License
Pal is made available as open source under the terms of the [GNU General Public License v3.0](https://opensource.org/licenses/GPL-3.0).

