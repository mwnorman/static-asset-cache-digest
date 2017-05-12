# static-asset-cache-digest
Maven plugin to clone a directory and rename its static assets to include a digest hash

This plugin performs a single transformation: walk the *source* directory tree, cloning to
the *target* directory. Along the way, for the identified file types (identified by extension)
calculate a digest-hash of the file's content and rename the file to include the hash-value.
