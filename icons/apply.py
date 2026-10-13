import sys, gen
key = sys.argv[1].upper()
bg, fg = gen.CONCEPTS[key]()
open('/home/claude/photobox/res/drawable/ic_launcher_bg.xml','w').write(gen.to_vector(bg))
open('/home/claude/photobox/res/drawable/ic_launcher_fg.xml','w').write(gen.to_vector(fg))
print("applied concept", key)
