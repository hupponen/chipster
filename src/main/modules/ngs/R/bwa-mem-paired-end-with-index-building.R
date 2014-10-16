# TOOL bwa-mem-paired-end-with-index-building.R: "BWA MEM for paired-end reads and own genome" (BWA aligns reads to genomes and transcriptomes using BWA MEM algorithm. Results are sorted and indexed bam files, which are ready for viewing in the Chipster genome browser. 
# Note that this BWA MEM tool requires that you have imported the reference genome to Chipster in fasta format. If you would like to align paired-end reads against publicly available genomes, please use the tool \"BWA MEM for paired-end reads\".)
# INPUT reads1.txt: "Reads to align" TYPE GENERIC 
# INPUT reads2.txt: "Reads to align" TYPE GENERIC 
# INPUT genome.txt: "Reference genome" TYPE GENERIC
# OUTPUT bwa.bam 
# OUTPUT bwa.bam.bai 
# OUTPUT bwa.log 
# PARAMETER organism: "Genome or transcriptome" TYPE [Arabidopsis_thaliana.TAIR10.22, Bos_taurus.UMD3.1.75, Canis_familiaris.CanFam3.1.75, Drosophila_melanogaster.BDGP5.75, Gallus_gallus.Galgal4.75, Gasterosteus_aculeatus.BROADS1.75, Halorubrum_lacusprofundi_atcc_49239.GCA_000022205.1.22, Homo_sapiens.GRCh37.75, Mus_musculus.GRCm38.75, Ovis_aries.Oar_v3.1.75, Rattus_norvegicus.Rnor_5.0.75, Schizosaccharomyces_pombe.ASM294v2.22, Sus_scrofa.Sscrofa10.2.75, Vitis_vinifera.IGGP_12x.22, Yersinia_enterocolitica_subsp_palearctica_y11.GCA_000253175.1.22] DEFAULT Homo_sapiens.GRCh37.75 (Genome or transcriptome that you would like to align your reads against.)
# PARAMETER mode: "Data source" TYPE [ normal: " Illumina, 454, IonTorrent reads longer than about 70 base pairs", pacbio: "PacBio subreads"] DEFAULT normal (Read types to align)

# KM 24.8.2011
# AMS 19.6.2012 Added unzipping
# AMS 11.11.2013 Added thread support

# check out if the file is compressed and if so unzip it
source(file.path(chipster.common.path, "zip-utils.R"))
unzipIfGZipFile("reads1.txt")
unzipIfGZipFile("reads2.txt")
unzipIfGZipFile("genome.txt")

# bwa
bwa.binary <- file.path(chipster.tools.path, "bwa", "bwa mem")
bwa.index.binary <- file.path(chipster.module.path, "shell", "check_bwa_index.sh")
command.start <- paste("bash -c '", bwa.binary)

# Do indexing
print("Indexing the genome...")
system("echo Indexing the genome... > bwa.log")
check.command <- paste ( bwa.index.binary, "genome.txt| tail -1 ")
genome.dir <- system(check.command, intern = TRUE)
bwa.genome <- file.path( genome.dir , "genome.txt")

mode.parameters <- ifelse(mode == "pacbio", "-x pacbio", "")

# command ending
command.end <- paste(bwa.genome, "reads1.txt reads2.txt 1> alignment.sam 2>> bwa.log'")

# run bwa alignment
bwa.command <- paste(command.start, mode.parameters, command.end)

echo.command <- paste("echo '", bwa.binary , mode.parameters, bwa.genome, "reads.txt ' > bwa.log" )
#stop(paste('CHIPSTER-NOTE: ', bwa.command))
system(echo.command)
system(bwa.command)
		
# samtools binary
samtools.binary <- c(file.path(chipster.tools.path, "samtools", "samtools"))

# convert sam to bam
system(paste(samtools.binary, "view -bS alignment.sam -o alignment.bam"))

# sort bam
system(paste(samtools.binary, "sort alignment.bam alignment.sorted"))

# index bam
system(paste(samtools.binary, "index alignment.sorted.bam"))

# rename result files
system("mv alignment.sorted.bam bwa.bam")
system("mv alignment.sorted.bam.bai bwa.bam.bai")