"""Explicit regeneration of only this batch; previous manual adaptations are retained."""
import sys
import import_business as importer

prefixes=("blocks/entity/storage/","blocks/entity/ECOMachineInterface", "impl/storage/transfer/",
    "multiblock/cluster/NEStorageCluster", "multiblock/calculator/NEStorageCluster",
    "gui/storage/StorageHostUI", "gui/storage/StoragePanelState", "gui/storage/StoragePanelTextures", "gui/common/HostText", "util/ICellHost", "util/CellHostItemHandler", "api/storage/ICellHost")
for path in importer.BUSINESS:
    if path.startswith(prefixes):
        sys.argv.extend(["--reapply-path","src/main/java/"+importer.PACKAGE+path])
for path in importer.TESTS:
    if path.startswith(("blocks/entity/storage/", "impl/storage/transfer/", "impl/storage/StorageInterfaceFilter", "gui/common/HostText")):
        sys.argv.extend(["--reapply-path","src/test/java/"+importer.PACKAGE+path])
importer.main()
