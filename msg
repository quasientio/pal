
Change message IDs from UUID to simple string

Rationale: to relax the requirement for rpc messages
to be identified by a string instead of a java UUID
serves the purpose of easing peer integration with
systems that may want to simply identify messages
with an integer sequence or another form of UID.
Also, uniqueness of ID's should not be enforced by
Pal but by systems using it.
