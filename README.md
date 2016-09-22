# home-api

![Build Status](https://travis-ci.org/etiago/home-api.svg?branch=master)

This project was my first-ever project in Clojure. After having started to use emacs and elisp to customize my emacs, I thought I wanted to give functional programming a good and decent try. Clojure seemed like a good candidate since it harnesses the power of the JVM, has a great community behind it and... brackets galore! :smile: No, but seriously, it's a Lisp like elisp, but with the JVM behind it, what's not to like.

In ```home-api``` I've created a RESTful API to serve my home's surveillance cameras as REST resources. At home I have FOSCAM cameras to monitor my home against instrusion, but I don't really trust these cameras to be exposed directly to the web. Furthermore, their API is a cumbersome and difficult to remember URL which goes beyond my use: enable and disable the camera's motion detection.

At its core, the ```home-api``` application maps inbound HTTP requests to outbound HTTP requests, being in fact a glorified router/proxy.

## Usage

The ```home-api``` project makes use of Leiningen. Before you start, be sure to run ```lein deps``` to install all the required dependencies.

Afterwards create a ```resources/``` folder and place a ```config.edn``` file in it. Its contents should be as follows:

```clojure
{:api-key "Here you will generate a random hash which you need to suply in the query string."
 :urls
 {:front
  {:enable "full_url_to_enable_your_camera"
   :disable "full_url_to_disable_your_camera"
   :status "full_url_to_get_your_cameras_status"}
 }
}
```

Keep in mind that you can specify more map keys values in the ```:urls``` map and these will automatically be available in your REST API. For example, the following is also valid:
```clojure
{:api-key "YOUR_RANDOMLY_GENERATED_KEY"
 :urls
 {:front
  {:enable "full_url_to_enable_your_camera"
   :disable "full_url_to_disable_your_camera"
   :status "full_url_to_get_your_cameras_status"}
  :garden
  {:snap "full_url_to_take_a_snapshot"}
 }
}
```

And allows you to access the endpoint ```/cameras/garden/snap```.

Once you have your configuration in place, you can start the server with ```lein ring server```.

## License

Copyright © 2016 Tiago Espinha

