DESCRIPTION = "Intel Runtime Development Tools"
HOMEPAGE = "https://www.intel.com"

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "${RDK_TOOLS_SOURCE} \
           file://rdk-grr-deliver11-include-linux-ethtool.h.patch \
           file://rdk-grr-deliver11-include-uapi-linux-ethtool.h.patch \
           file://rdk-grr-deliver11-remove-definition-of-cpu_online.patch \
           file://rdk-grr-deliver11-rename-nla_strlcpy-to-nla_strscpy.patch \
           file://rdk-grr-deliver11-use-namespace-CRYPTO_INTERNAL-for.patch \
           file://rdk-grr-deliver11-add-missing-unused-meson-option.patch \
           file://rdk-grr-deliver11-ies-api-Add-Wno-error-address-to-b.patch \
          "

# Define this if the files exist.  Usually done in a template.
# RDK_TOOLS_SOURCE = "file://rdk_user_src.tar.xz file://rdk_klm_src.tar.xz"
#
RDK_TOOLS_SOURCE ??= ""

inherit module meson pkgconfig

# Currently supported version
RDK_TOOLS_VERSION ?= "grr_deliver11"

S = "${WORKDIR}/rdk"
PR = "${RDK_TOOLS_VERSION}"

COMPATIBLE_MACHINE = "null"

DEPENDS = "virtual/kernel libnl libpcap openssl rsync-native thrift meson-native lttng-ust lttng-tools"
RDEPENDS_${PN} += "lttng-ust lttng-tools"

#RDK Tools installed path
RDK_INSTALL_DIR ?= "/opt/intel/rdk-tools"

export KSRC = "${STAGING_KERNEL_BUILDDIR}"
export SDKTARGETSYSROOT = "${STAGING_DIR_TARGET}"
export OECORE_NATIVE_SYSROOT = "${STAGING_DIR_NATIVE}"

# GRR RDK delivery 11 is only compatible with lttng v2.12, not v2.13
# So disable lttng support here.
export RDK_LTTNG_ENABLE = "false"

TARGET_CC_ARCH += "${LDFLAGS}"
EXTRA_OEMAKE = "V=1"

do_configure[noexec] = "1"

do_compile () {

	# RDK meson.build is not compatible with meson-wrapper changed in oe-core
	# commit 87d4f6d176f2 ("meson: provide relocation script and native/cross wrappers also for meson-native").
	# So remove meson-wrapper in RDK build and use the "real" meson.
	rm -f ${STAGING_DIR_NATIVE}/usr/bin/meson
	ln -s ${STAGING_DIR_NATIVE}/usr/bin/meson.real ${STAGING_DIR_NATIVE}/usr/bin/meson

	cd ${S}
	oe_runmake modules
	oe_runmake cpk-ae-lib
	oe_runmake netd-lib
	oe_runmake qat_lib
	oe_runmake ies_api_install
	oe_runmake -j1 nura
	oe_runmake cli
}

do_install () {
	oe_runmake -C ${S} install

	install -d ${D}${RDK_INSTALL_DIR}
	cp -r ${S}/install/*  ${D}${RDK_INSTALL_DIR}

	install -d ${D}${RDK_INSTALL_DIR}${nonarch_base_libdir}/modules/${KERNEL_VERSION}
	mv ${D}${RDK_INSTALL_DIR}/drivers/*.ko ${D}${RDK_INSTALL_DIR}${nonarch_base_libdir}/modules/${KERNEL_VERSION}

	install -d ${D}${nonarch_base_libdir}/firmware
	install -D -m 0644 ${D}${RDK_INSTALL_DIR}/drivers/qat_c4xxx.bin  ${D}${nonarch_base_libdir}/firmware/qat_c4xxx.bin
	install -D -m 0644 ${D}${RDK_INSTALL_DIR}/drivers/qat_c4xxx_mmp.bin ${D}${nonarch_base_libdir}/firmware/qat_c4xxx_mmp.bin

	install -D -m 0644 ${S}/install/lib/firmware/intel/ice_sw/ddp/ice_sw.pkg ${D}${nonarch_base_libdir}/firmware/intel/ice_sw/ddp/ice_sw.pkg
	install -D -m 0644 ${S}/install/lib/firmware/intel/eth56g/phy-fw.bin ${D}${nonarch_base_libdir}/firmware/intel/eth56g/phy-fw.bin

	sed -i 's#prefix=.*#prefix=${prefix}#' ${D}${RDK_INSTALL_DIR}/lib/pkgconfig/*.pc

	rm -rf ${D}/etc
}

SYSROOT_DIRS += "/opt"

FILES:${PN}-dev = "${RDK_INSTALL_DIR}/lib/libies*.so"
FILES:${PN}-staticdev = "${RDK_INSTALL_DIR}/lib/libies*.a"
FILES:${PN} += "${RDK_INSTALL_DIR} \
                /lib "

INSANE_SKIP:${PN} = "already-stripped ldflags"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
