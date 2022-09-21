import sys
import pprint
import json

stuff = {}
myin = json.load(sys.stdin)
stuff["stdin"] = myin
stuff["args"]=sys.argv[1:]
json.dump(stuff, sys.stdout)