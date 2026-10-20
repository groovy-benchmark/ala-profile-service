package au.org.ala.profile

import au.org.ala.profile.util.JobType

import jakarta.validation.constraints.NotNull

class JobController extends BaseController {

    JobService jobService

    def createJob(@NotNull String jobType) {
        JobType type = JobType.byName(jobType)
        if (!request.getJSON() || !type) {
            badRequest "A valid jobType and a json body containing the job parameters are required"
        } else {
            Job job = jobService.createJob(type, request.getJSON())

            success([jobId: job.jobId])
        }
    }

    def listAllPendingJobs(String jobType) {
        JobType type = JobType.byName(jobType)
        List<Job> jobs = jobService.listPendingJobs(type)

        success([jobs: jobs])
    }

    def getNextPendingJob(String jobType) {
        JobType type = jobType ? jobType.toUpperCase() as JobType : null
        Job job = jobService.listPendingJobs(type)?.first()

        success(job?:[:])
    }

    def updateJob(@NotNull String jobId) {
        if (!request.getJSON()) {
            badRequest "A json body containing the job parameters is required"
        } else {
            Job job = Job.findByJobId(jobId)

            if (job) {
                job = jobService.updateJob(jobId, request.getJSON())

                success job?:[:]
            } else {
                notFound "No job was found for id ${jobId}"
            }
        }
    }

    def deleteJob(@NotNull String jobId) {
        Job job = Job.findByJobId(jobId)

        if (job) {
            boolean deleted = jobService.deleteJob(jobId)

            if (deleted) {
                success([:])
            } else {
                saveFailed "Failed to delete job ${jobId}"
            }
        } else {
            notFound "No job was found for id ${jobId}"
        }
    }
}
