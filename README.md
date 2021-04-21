# Channel - a port of GoLang channels to Java
[![Build](https://github.com/yruslan/channel_java/workflows/Build/badge.svg)](https://github.com/yruslan/channel_java/actions)

> Go channels provide synchronization and messaging, 'select' provides multi-way concurrent control.
>
> _Rob Pike_ - [Concurrency is not parallelism](https://www.youtube.com/watch?v=oV9rvDllKEg)
/ [another link with better slide view](https://www.youtube.com/watch?v=qmg1CF3gZQ0)

This is one of Java ports of Go channels. The idea of this particular port is to match as much as possible the features
provided by GoLang so channels can be used for concurrency coordination. The library uses locks, conditional variables and
semaphores as underlying concurrency primitives so the performance is not expected to match applications written in Go.

There is also a sibling project - channels for Scala: https://github.com/yruslan/channel_scala

## Link

The project hasn't been released yet.

## Motivation
GoLang channels are based on CSP model by Tony Hoare (1978). CSP channels provide an extremely simple and uniform
building block for designing concurrent applications.  
