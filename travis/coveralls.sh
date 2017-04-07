#!/bin/bash

# POST job to coveralls.  NOTE that running lein cloverage under
# travis automatically writes the appropriate details of the
# repo/build etc into the coveralls.json.

COVERALLS_URL='https://coveralls.io/api/v1/jobs'
curl -F 'json_file=@target/coverage/coveralls.json' "$COVERALLS_URL"
