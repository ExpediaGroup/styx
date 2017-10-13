# Building and viewing docs

If/when building on Mac OSX, ensure that Xcode command line tools 
are installed. Install them with *xcode-select* command: 

    $ xcode-select --install

Next, from the Styx docs directory, install *jekyll*:

    $ bundle install
    
Finally, start Jekyll service. This opens a little web server
to render the documentation.
    
    $ bundle exec jekyll serve

To read the documentation, point your browser to *http://localhost:4000*.
