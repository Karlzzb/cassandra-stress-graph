#! /bin/sh
test() {
  CSTRESS="/srv/scratch/app/dsc-cassandra-2.1.5/tools/bin/cassandra-stress"
  GRAPH_TOOL="/srv/scratch/app/stressgraph.jar"

  threads=$1

  testparm=$2

  title="bench-$testparm"

  stress_mode=$4
  model_opts="duration=120m no-warmup"
  rateopt="-rate threads=$threads"
  version="${title}-$3"
  stress_log="/srv/scratch/rpt/cassandra-stress-$version.log"

  stress_opts="$stress_mode $model_opts $rateopt -log file=$stress_log -node 192.168.56.101,192.168.56.102,192.168.56.103"

  graph="/srv/scratch/rpt/${title}.html"

  cat /srv/scratch/work/pass.txt | sudo -S $CSTRESS $stress_opts -schema keyspace="stress_${testparm}_$3"
  sleep 10
  cat /srv/scratch/work/pass.txt | sudo -S java -jar $GRAPH_TOOL -m $stress_mode -v $version -t $title -i $stress_log -o $graph -sa "$stress_opts"
}

testmodel() {
  /usr/bin/pdsh -w 192.168.56.[101-103] -l root "sed -i \"s/\($1:\).*/\1 $2/g\" /etc/dse/cassandra/cassandra.yaml"
  #/usr/bin/pdsh -w 192.168.56.[101-103] -l root "cat /etc/dse/cassandra/cassandra.yaml |grep $1:"
  /usr/bin/pdsh -w 192.168.56.[101-103] -l root nodetool flush
  /usr/bin/pdsh -w 192.168.56.101 -l root "cqlsh -e \"DROP KEYSPACE IF EXISTS stress_$1_$2\"" 
  /usr/bin/pdsh -w 192.168.56.[101-103] -l root nodetool clearsnapshot
  #/usr/bin/pdsh -w 192.168.56.101 -l root "cqlsh -e \"CREATE KEYSPACE stress_$1_$2 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2}\""
  /usr/bin/pdsh -w 192.168.56.[101-103] -l root nodetool drain
  /usr/bin/pdsh -w 192.168.56.[101-103] -l root /etc/init.d/dse restart
  sleep 60
  test 12 $1 $2 $3
}


testmodel "memtable_flush_writers" 4 "WRITE" 
testmodel "memtable_flush_writers" 6 "WRITE" 
testmodel "memtable_flush_writers" 8 "WRITE" 

testmodel "memtable_allocation_type" "heap_buffers" "WRITE"  
testmodel "memtable_allocation_type" "offheap_objects" "WRITE" 

testmodel "concurrent_writes" 16 "MIXED" 
testmodel "concurrent_writes" 32 "MIXED" 
testmodel "concurrent_writes" 64 "MIXED" 
testmodel "concurrent_writes" 128 "MIXED"

testmodel "concurrent_reads" 16 "MIXED"
testmodel "concurrent_reads" 32 "MIXED"
testmodel "concurrent_reads" 64 "MIXED"
testmodel "concurrent_reads" 128 "MIXED"

testmodel "concurrent_counter_writes" 16 "COUNTER_WRITE"
testmodel "concurrent_counter_writes" 32 "COUNTER_WRITE"
testmodel "concurrent_counter_writes" 64 "COUNTER_WRITE"
testmodel "concurrent_counter_writes" 128 "COUNTER_WRITE"
