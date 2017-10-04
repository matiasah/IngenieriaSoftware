# Managing static premium price lists

## Premium list file format

Nomulus comes with a `StaticPremiumListPricingEngine` that determines premium
prices of domain labels (i.e. the part of the domain name without the TLD) by
checking for their presence on a list of prices in Datastore. `nomulus` is used
to load and update these lists from flat text files. The format of this list is
simple: It is a newline-delimited CSV text file with each line containing the
label and its price (including currency specifier in ISO-4217 format). Any
individual label may not appear more than once in the file. Here's an example of
the formatting:

```
premium,USD 100
superpremium,USD 500
```

By convention, premium lists are named after the TLD they apply to (with one
premium list per TLD). If the above example applied to the `exampletld` example,
then the file would be saved as `exampletld.txt` and the domain name
`premium.exampletld` would cost $100. It is also possible to have a single
premium list that is used on multiple TLDs, for which a different naming
convention would be used.

It is recommended that you store your premium list .txt files in a backed-up
location that is accessible to all members of your team (ideally in a source
control system for revision tracking). These files should be thought of as the
canonical versions of your premium lists. Note that there is no provided way to
reconstruct a premium list .txt file from the premium list that is loaded into
Datastore (though in principle it would be easy to do by writing a tool to do
so), so don't lose those .txt files.

## Creating a premium list

Once the file containing the premium prices is ready, run the
`create_premium_list` command to load it into Datastore as follows:

```shell
$ nomulus -e {ENVIRONMENT} create_premium_list -n exampletld -i exampletld.txt

You are about to save the premium list exampletld with 2 items:
Perform this command? (y/N): y
Successfully saved premium list exampletld
```

`-n` is the name of the list to be created, and `-i` is the input filename. Note
that the convention of naming premium lists after the TLD they are intended to
be used for is enforced unless the override parameter `-o` is passed, which
allows premium lists to be created with any name.

## Updating a premium list

If the premium list already exists and you want to update it with new prices
from a text file, the procedure is exactly the same, except using the
`update_premium_list` command as follows:

```shell
$ nomulus -e {ENVIRONMENT} update_premium_list -n exampletld -i exampletld.txt

You are about to save the premium list exampletld with 2 items:
Perform this command? (y/N): y
Successfully saved premium list exampletld
```

## Applying a premium list to a TLD

Separate from the management of the contents of individual premium lists, a
premium list must first be applied to a TLD before it will take effect. You will
only need to do this when first creating a premium list; once it has been
applied, it stays applied, and updates to the list are effective automatically.
Note that each TLD can have no more than one premium list applied to it. To
apply a premium list to a TLD, run the `update_tld` command with the following
parameter:

```shell
$ nomulus -e {ENVIRONMENT} update_tld exampletld --premium_list exampletld
Update Registry@exampletld
premiumList -> [null, Key<?>(EntityGroupRoot("cross-tld")/PremiumList("exampletld"))]

Perform this command? (y/N): y
Updated 1 entities.
```

## Checking which premium list is applied to a TLD

The `get_tld` command shows which premium list is applied to a TLD (along with
all other information about a TLD). It is used as follows:

```shell
$ nomulus -e {ENVIRONMENT} get_tld exampletld
[ ... snip ... ]
premiumList=Key<?>(EntityGroupRoot("cross-tld")/PremiumList("exampletld"))
[ ... snip ... ]
```

## Listing all available premium lists

The `list_premium_lists` command is used to list all premium lists in Datastore.
It takes no arguments and displays a simple list of premium lists as follows:

```shell
$ nomulus -e {ENVIRONMENT} list_premium_lists
exampletld
someotherlist
```
